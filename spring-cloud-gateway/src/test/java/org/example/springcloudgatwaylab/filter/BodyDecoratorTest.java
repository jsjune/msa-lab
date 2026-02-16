package org.example.springcloudgatwaylab.filter;

import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.example.springcloudgatwaylab.service.LogStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Body Decorator - 요청/응답 본문 캡처")
class BodyDecoratorTest {

    private LoggingGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoggingGlobalFilter(
                mock(LogStorageService.class),
                mock(KafkaMetadataSender.class),
                mock(ReactiveStringRedisTemplate.class)
        );
    }

    private DataBuffer toDataBuffer(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
    }

    @Test
    @DisplayName("RequestBufferLoggingDecorator: 요청 본문을 ByteArrayOutputStream에 복사한다")
    void requestDecorator_copiesBodyToOutputStream() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(toDataBuffer("hello world")));

        LoggingGlobalFilter.RequestBufferLoggingDecorator decorator =
                filter.new RequestBufferLoggingDecorator(request, outputStream);

        // when
        StepVerifier.create(decorator.getBody().then())
                .verifyComplete();

        // then
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    @DisplayName("RequestBufferLoggingDecorator: 원본 DataBuffer의 read position을 복원한다")
    void requestDecorator_restoresReadPosition() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataBuffer buffer = toDataBuffer("test data");
        int originalReadableCount = buffer.readableByteCount();

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(buffer));

        LoggingGlobalFilter.RequestBufferLoggingDecorator decorator =
                filter.new RequestBufferLoggingDecorator(request, outputStream);

        // when — consume the body flux
        StepVerifier.create(decorator.getBody().doOnNext(buf -> {
            // then — buffer should still be fully readable
            assertThat(buf.readableByteCount()).isEqualTo(originalReadableCount);
        }).then()).verifyComplete();
    }

    @Test
    @DisplayName("ResponseBufferLoggingDecorator: 응답 본문을 ByteArrayOutputStream에 복사한다")
    void responseDecorator_copiesBodyToOutputStream() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpResponse response = new MockServerHttpResponse();

        LoggingGlobalFilter.ResponseBufferLoggingDecorator decorator =
                filter.new ResponseBufferLoggingDecorator(response, outputStream);

        // when
        StepVerifier.create(decorator.writeWith(Flux.just(toDataBuffer("response body"))))
                .verifyComplete();

        // then
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("response body");
    }

    @Test
    @DisplayName("빈 본문일 때 ByteArrayOutputStream에 0 bytes가 기록된다")
    void requestDecorator_emptyBody_writesZeroBytes() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.empty());

        LoggingGlobalFilter.RequestBufferLoggingDecorator decorator =
                filter.new RequestBufferLoggingDecorator(request, outputStream);

        // when
        StepVerifier.create(decorator.getBody().then())
                .verifyComplete();

        // then
        assertThat(outputStream.size()).isZero();
    }
}
