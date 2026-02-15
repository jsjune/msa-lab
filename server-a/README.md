# server-a

Spring MVC 기반 백엔드 서버. 서비스 체인 호출의 시작점으로, gateway를 통해 server-b로 요청을 전파한다.

## 포트

- `8081` (기본값, `SERVER_PORT` 환경변수로 변경 가능)

## API

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /hello` | 헬스체크 (`Hello from Server A`) |
| `GET /pod-info` | Pod 메타정보 반환 (txId, podName, podIp, namespace, nodeName) |
| `GET /chain` | 자기 정보 + gateway 경유 server-b `/chain` 호출 결과를 합쳐서 반환 |

## 체인 호출 흐름

```
server-a /chain → Gateway(/server-b/chain) → server-b /chain → Gateway(/server-c/chain) → server-c /chain
```

`X-Tx-Id` 헤더를 WebClient로 전파하여 전체 체인이 하나의 트랜잭션 ID로 추적됨.

## 빌드 및 실행

```bash
./gradlew :server-a:bootRun
./gradlew :server-a:test
```

## 설정

| 환경변수 | 기본값 | 설명 |
|----------|--------|------|
| `SERVER_PORT` | `8081` | 서버 포트 |
| `GATEWAY_URL` | `http://localhost:8000` | 체인 호출 시 사용할 게이트웨이 주소 |

K8s 배포 시 Downward API로 `POD_NAME`, `POD_IP`, `POD_NAMESPACE`, `NODE_NAME` 환경변수 주입.

## 의존성

- Spring Boot Web, Spring WebFlux (WebClient용)
