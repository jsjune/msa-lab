# spring-cloud-gateway

Spring Cloud Gateway(WebFlux) 기반 로깅 게이트웨이. 모든 요청/응답의 body와 header를 메모리 버퍼에 캡처하여 MinIO에 업로드하고, 메타데이터는 Kafka로 전송한다.

## 포트

- `8000` (기본값)

## 핵심 동작

1. `LoggingGlobalFilter` (HIGHEST_PRECEDENCE)가 모든 요청을 가로챔
2. `X-Tx-Id` 헤더 생성 또는 전파, hop 카운터 관리
3. req/res body를 `ByteArrayOutputStream`에 캡처 (로컬 파일 I/O 없음)
4. req/res header를 JSON으로 직렬화
5. `doFinally`에서 4개 오브젝트를 MinIO에 업로드: `{txId}-hop{n}.{req|res|req.header|res.header}`
6. 메타데이터(txId, hop, path, status, duration, bodyUrl 등)를 Kafka 토픽 `gateway-meta-logs`로 전송

## 주요 컴포넌트

| 클래스 | 패키지 | 역할 |
|--------|--------|------|
| `LoggingGlobalFilter` | filter | 요청/응답 가로채기, 메모리 버퍼 캡처, 업로드/메타데이터 전송 조율 |
| `LogStorageService` | service | 스토리지 추상화 인터페이스 (`upload`, `getStorageBaseUrl`) |
| `MinioStorageService` | service | MinIO `putObject` 구현 (byte[] → ByteArrayInputStream) |
| `KafkaMetadataSender` | service | Kafka fire-and-forget 전송 (acks=0, retries=0) |
| `KafkaConfig` | config | Kafka producer factory, 토픽 자동생성 (3 partitions, compacted) |
| `GatewayConfiguration` | config | 라우트 정의 (server-a/b/c, stripPrefix=1) |
| `LogReaderController` | controller | `GET /logs/body?bodyUrl=` — MinIO에서 바디 조회 |

## 빌드 및 실행

```bash
./gradlew :spring-cloud-gateway:build
./gradlew :spring-cloud-gateway:bootRun
./gradlew :spring-cloud-gateway:test
```

## 설정

`src/main/resources/application.yml` 참고. 주요 환경변수:

| 환경변수 | 기본값 | 설명 |
|----------|--------|------|
| `KAFKA_SERVERS` | `192.168.137.10:32100` | Kafka 브로커 |
| `MINIO_ENDPOINT` | `http://192.168.137.10:9000` | MinIO API |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO 인증 |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO 인증 |
| `LOG_BUCKET` | `gateway-logs` | MinIO 버킷 |
| `SERVER_{A,B,C}_URL` | `http://localhost:808{1,2,3}` | 백엔드 서버 주소 |

## 의존성

- Spring Cloud Gateway (WebFlux), Spring Kafka, MinIO SDK 8.6.0, Jackson
