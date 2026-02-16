# Spring Cloud Gateway Lab

Spring Cloud Gateway(WebFlux) 기반 API 게이트웨이와 백엔드 서버 3종으로 구성된 MSA 로깅 파이프라인 실습 프로젝트.

## 모듈 구성

| 모듈 | 타입 | 포트 | 설명 |
|------|------|------|------|
| `spring-cloud-gateway` | Spring Cloud Gateway (WebFlux) | 8000 | 로깅 게이트웨이 |
| `server-a` | Spring MVC + WebClient | 8081 | 백엔드 A (체인 시작점) |
| `server-b` | Spring MVC + WebClient | 8082 | 백엔드 B (체인 중간) |
| `server-c` | Spring MVC | 8083 | 백엔드 C (체인 종단) |

## 로깅 아키텍처 (Dual Pipeline)

### Fast Track — 실시간 메타데이터
TxId, path, status, duration, error, bodyUrl → **Kafka** 토픽 (`gateway-meta-logs`)

### Slow Track — 바디 저장
req/res body + header → **메모리 버퍼(ByteArrayOutputStream)** → **MinIO** 직접 업로드 (로컬 파일 I/O 없음)

오브젝트 경로: `gateway-logs/{yyyy/MM/dd}/{txId}-hop{n}.{req|res|req.header|res.header}`

## 서비스 체인 호출 흐름

```
Client → Gateway → server-a /chain
                      ↓ (X-Tx-Id 전파)
                    Gateway → server-b /chain
                                ↓ (X-Tx-Id 전파)
                              Gateway → server-c /chain (종단, 자기 정보만 반환)
```

게이트웨이를 재진입할 때마다 hop 카운터가 증가하여 같은 txId로 각 hop의 로그가 분리 저장됨.

## 인프라 환경 (Kubernetes)

### 클러스터 구성

| 노드 | 역할 | IP | OS |
|------|------|-----|-----|
| master-1 | control-plane | 192.168.137.10 | Rocky Linux 9.7 |
| worker-1 | worker | 192.168.137.11 | Rocky Linux 9.7 |
| worker-2 | worker | 192.168.137.12 | Rocky Linux 9.7 |
| worker-3 | worker | 192.168.137.13 | Rocky Linux 9.7 |

- Kubernetes v1.34.2, containerd 1.7.29

### Kafka (Strimzi Operator)

| 항목 | 값 |
|------|-----|
| 클러스터명 | `df-cluster` |
| Kafka 버전 | 4.1.1 |
| 브로커 | 3개 (`df-cluster-broker-{0,1,2}`) |
| 컨트롤러 | 3개 (`df-cluster-controller-{3,4,5}`) |
| 네임스페이스 | `default` |
| 내부 접속 (plain) | `df-cluster-kafka-bootstrap:9092` |
| 외부 접속 (NodePort) | `192.168.137.10:32100` (broker별: 32101, 32102, 32103) |
| Kafka UI | `http://192.168.137.10:31180` (NodePort) |

### MinIO (K8s StatefulSet)

| 항목 | 값 |
|------|-----|
| 배포 방식 | StatefulSet (`minio-0`) in `default` namespace |
| 이미지 | `minio/minio:latest` |
| 스토리지 | 20Gi PVC (`nfs-data-sc` StorageClass) |
| 내부 접속 (API) | `minio:9000` (ClusterIP) |
| 내부 접속 (Console) | `minio:9001` (ClusterIP) |
| 외부 접속 (API) | `http://192.168.137.10:30900` (NodePort) |
| 외부 접속 (Console) | `http://192.168.137.10:30901` (NodePort) |
| 인증 정보 | K8s Secret `minio-secret` |

### 모니터링

| 서비스 | 접속 주소 |
|--------|-----------|
| Grafana | `http://192.168.137.10:30001` |
| Prometheus | `http://192.168.137.10:30009` |

## 빌드 및 실행

```bash
# 전체 빌드
./gradlew build

# 개별 모듈 실행
./gradlew :spring-cloud-gateway:bootRun   # Gateway (8000)
./gradlew :server-a:bootRun               # Server A (8081)
./gradlew :server-b:bootRun               # Server B (8082)
./gradlew :server-c:bootRun               # Server C (8083)

# 테스트
./gradlew test                             # 전체
./gradlew :spring-cloud-gateway:test       # Gateway만
```

### K8s 배포

```bash
kubectl apply -f k8s/
```

## 주요 설정 (application.yml)

### 게이트웨이

| 설정 | 환경변수 | 기본값 |
|------|----------|--------|
| `gateway.routes.server-{a,b,c}.uri` | `SERVER_{A,B,C}_URL` | `http://localhost:808{1,2,3}` |
| `gateway.logs.storage.type` | - | `minio` |
| `gateway.logs.storage.bucket` | `LOG_BUCKET` | `gateway-logs` |
| `gateway.logs.minio.endpoint` | `MINIO_ENDPOINT` | `http://192.168.137.10:30900` |
| `gateway.logs.minio.{access,secret}-key` | `MINIO_{ACCESS,SECRET}_KEY` | `minioadmin` |
| `spring.kafka.bootstrap-servers` | `KAFKA_SERVERS` | `192.168.137.10:32100` |

### 백엔드 서버

| 설정 | 환경변수 | 기본값 |
|------|----------|--------|
| `server.port` | `SERVER_PORT` | `8081` / `8082` / `8083` |
| `gateway.url` | `GATEWAY_URL` | `http://localhost:8000` |

## API 엔드포인트

### 게이트웨이 (`:8000`)
- `GET /server-a/**` → server-a로 프록시 (stripPrefix=1)
- `GET /server-b/**` → server-b로 프록시
- `GET /server-c/**` → server-c로 프록시
- `GET /logs/body?bodyUrl=s3://...` → MinIO에서 req/res 바디 조회

### 백엔드 서버 공통
- `GET /hello` — 헬스체크
- `GET /pod-info` — Pod 메타정보 (txId, podName, podIp, namespace, nodeName)
- `GET /chain` — 다음 서버로 체인 호출 (server-c는 종단)

## 기술 스택

- Java 21, Spring Boot 4.0.2, Spring Cloud 2025.1.0
- Spring Cloud Gateway (WebFlux), Spring Kafka, MinIO SDK 8.6.0
- Strimzi (Kafka on K8s), MinIO (K8s StatefulSet)
