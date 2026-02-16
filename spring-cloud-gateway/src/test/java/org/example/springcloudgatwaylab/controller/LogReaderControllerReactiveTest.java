package org.example.springcloudgatwaylab.controller;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioAsyncClient;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LogReaderController - 리액티브 조회 흐름")
class LogReaderControllerReactiveTest {

    private MinioAsyncClient minioClient;
    private LogReaderController controller;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioAsyncClient.class);
        controller = new LogReaderController(minioClient);
    }

    private GetObjectResponse mockGetObjectResponse(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new GetObjectResponse(
                Headers.of(),
                "test-bucket",
                "",
                "test-object",
                new ByteArrayInputStream(bytes)
        );
    }

    @Test
    @DisplayName("정상 bodyUrl로 request/response 본문을 포함한 200 응답을 반환한다")
    void getLogByBodyUrl_validUrl_returns200WithBodies() throws Exception {
        // given
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(CompletableFuture.completedFuture(mockGetObjectResponse("{\"msg\":\"req\"}")))
                .thenReturn(CompletableFuture.completedFuture(mockGetObjectResponse("{\"msg\":\"res\"}")));

        // when & then
        StepVerifier.create(controller.getLogByBodyUrl("s3://test-bucket/2026/02/16/tx1-hop1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(200);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).containsKey("request");
                    assertThat(body).containsKey("response");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("MinIO 연결 실패 시 404 Not Found를 반환한다")
    void getLogByBodyUrl_minioFailure_returns404() throws Exception {
        // given
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection refused")));

        // when & then
        StepVerifier.create(controller.getLogByBodyUrl("s3://test-bucket/2026/02/16/tx1-hop1"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(404);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("잘못된 bodyUrl 형식은 400 Bad Request를 반환한다")
    void getLogByBodyUrl_invalidUrl_returns400() {
        // when & then
        StepVerifier.create(controller.getLogByBodyUrl("no-slash"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode().value()).isEqualTo(400);
                    assertThat(response.getBody()).containsKey("error");
                })
                .verifyComplete();
    }
}
