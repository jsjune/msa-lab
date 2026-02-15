# server-c

Spring MVC 기반 백엔드 서버. 서비스 체인의 종단 노드로, 다른 서버를 호출하지 않고 자기 정보만 반환한다.

## 포트

- `8083` (기본값, `SERVER_PORT` 환경변수로 변경 가능)

## API

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /hello` | 헬스체크 (`Hello from Server C`) |
| `GET /pod-info` | Pod 메타정보 반환 (txId, podName, podIp, namespace, nodeName) |
| `GET /chain` | 자기 Pod 정보만 반환 (체인 종단) |

## 빌드 및 실행

```bash
./gradlew :server-c:bootRun
./gradlew :server-c:test
```

## 설정

| 환경변수 | 기본값 | 설명 |
|----------|--------|------|
| `SERVER_PORT` | `8083` | 서버 포트 |

K8s 배포 시 Downward API로 `POD_NAME`, `POD_IP`, `POD_NAMESPACE`, `NODE_NAME` 환경변수 주입.

## 의존성

- Spring Boot Web
