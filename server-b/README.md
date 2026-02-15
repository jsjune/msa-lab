# server-b

Spring MVC 기반 백엔드 서버. 서비스 체인의 중간 노드로, gateway를 통해 server-c로 요청을 전파한다.

## 포트

- `8082` (기본값, `SERVER_PORT` 환경변수로 변경 가능)

## API

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /hello` | 헬스체크 (`Hello from Server B`) |
| `GET /pod-info` | Pod 메타정보 반환 (txId, podName, podIp, namespace, nodeName) |
| `GET /chain` | 자기 정보 + gateway 경유 server-c `/chain` 호출 결과를 합쳐서 반환 |

## 빌드 및 실행

```bash
./gradlew :server-b:bootRun
./gradlew :server-b:test
```

## 설정

| 환경변수 | 기본값 | 설명 |
|----------|--------|------|
| `SERVER_PORT` | `8082` | 서버 포트 |
| `GATEWAY_URL` | `http://localhost:8000` | 체인 호출 시 사용할 게이트웨이 주소 |

K8s 배포 시 Downward API로 `POD_NAME`, `POD_IP`, `POD_NAMESPACE`, `NODE_NAME` 환경변수 주입.

## 의존성

- Spring Boot Web, Spring WebFlux (WebClient용)
