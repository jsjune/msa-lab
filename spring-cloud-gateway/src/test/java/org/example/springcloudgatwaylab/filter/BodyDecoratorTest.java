package org.example.springcloudgatwaylab.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Body Decorator - 요청/응답 본문 캡처")
class BodyDecoratorTest {

    private DataBuffer toDataBuffer(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
    }

    @Test
    @DisplayName("BodyCapturingRequestDecorator: 요청 본문을 ByteArrayOutputStream에 복사한다")
    void requestDecorator_copiesBodyToOutputStream() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(toDataBuffer("hello world")));

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, new AtomicBoolean(), 1024 * 1024);

        // when
        StepVerifier.create(decorator.getBody().then())
                .verifyComplete();

        // then
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    @DisplayName("BodyCapturingRequestDecorator: 원본 DataBuffer의 read position을 복원한다")
    void requestDecorator_restoresReadPosition() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataBuffer buffer = toDataBuffer("test data");
        int originalReadableCount = buffer.readableByteCount();

        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(buffer));

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, new AtomicBoolean(), 1024 * 1024);

        // when — consume the body flux
        StepVerifier.create(decorator.getBody().doOnNext(buf -> {
            // then — buffer should still be fully readable
            assertThat(buf.readableByteCount()).isEqualTo(originalReadableCount);
        }).then()).verifyComplete();
    }

    @Test
    @DisplayName("BodyCapturingResponseDecorator: 응답 본문을 ByteArrayOutputStream에 복사한다")
    void responseDecorator_copiesBodyToOutputStream() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpResponse response = new MockServerHttpResponse();

        BodyCapturingResponseDecorator decorator =
                new BodyCapturingResponseDecorator(response, outputStream, new AtomicBoolean(), 1024 * 1024);

        // when
        StepVerifier.create(decorator.writeWith(Flux.just(toDataBuffer("response body"))))
                .verifyComplete();

        // then
        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("response body");
    }

    @Test
    @DisplayName("요청 본문이 maxBodySize 이하이면 전체를 캡처한다 (truncation 없음)")
    void requestDecorator_bodyWithinLimit_fullCapture() {
        // given — 512 bytes body, 1024 bytes limit
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AtomicBoolean truncatedFlag = new AtomicBoolean(false);

        byte[] body = new byte[512];
        java.util.Arrays.fill(body, (byte) 'A');
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)));

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, truncatedFlag, 1024);

        // when
        StepVerifier.create(decorator.getBody().then()).verifyComplete();

        // then — 512 bytes 전체 캡처, truncated=false
        assertThat(outputStream.size()).isEqualTo(512);
        assertThat(truncatedFlag.get()).isFalse();
    }

    @Test
    @DisplayName("요청 본문이 maxBodySize 초과 시 maxBodySize까지만 캡처하고 truncatedFlag를 true로 설정한다")
    void requestDecorator_bodyExceedsLimit_truncatesAndSetsFlag() {
        // given — 600 bytes body, 512 bytes limit
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AtomicBoolean truncatedFlag = new AtomicBoolean(false);

        byte[] body = new byte[600];
        java.util.Arrays.fill(body, (byte) 'B');
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)));

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, truncatedFlag, 512);

        // when
        StepVerifier.create(decorator.getBody().then()).verifyComplete();

        // then — 512 bytes만 캡처, truncated=true
        assertThat(outputStream.size()).isEqualTo(512);
        assertThat(truncatedFlag.get()).isTrue();
    }

    @Test
    @DisplayName("응답 본문이 maxBodySize 초과 시 maxBodySize까지만 캡처하고 truncatedFlag를 true로 설정한다")
    void responseDecorator_bodyExceedsLimit_truncatesAndSetsFlag() {
        // given — 600 bytes response, 512 bytes limit
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AtomicBoolean truncatedFlag = new AtomicBoolean(false);
        MockServerHttpResponse response = new MockServerHttpResponse();

        byte[] body = new byte[600];
        java.util.Arrays.fill(body, (byte) 'C');

        BodyCapturingResponseDecorator decorator =
                new BodyCapturingResponseDecorator(response, outputStream, truncatedFlag, 512);

        // when
        StepVerifier.create(decorator.writeWith(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body))))
                .verifyComplete();

        // then — 512 bytes만 캡처, truncated=true
        assertThat(outputStream.size()).isEqualTo(512);
        assertThat(truncatedFlag.get()).isTrue();
    }

    @Test
    @DisplayName("멀티 청크 요청에서 누적 크기가 limit 초과 시 정확히 limit까지만 캡처한다")
    void requestDecorator_multiChunk_truncatesAtExactLimit() {
        // given — 2 chunks of 300 bytes each, 512 bytes limit
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AtomicBoolean truncatedFlag = new AtomicBoolean(false);

        byte[] chunk1 = new byte[300];
        byte[] chunk2 = new byte[300];
        java.util.Arrays.fill(chunk1, (byte) 'X');
        java.util.Arrays.fill(chunk2, (byte) 'Y');
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.just(
                        DefaultDataBufferFactory.sharedInstance.wrap(chunk1),
                        DefaultDataBufferFactory.sharedInstance.wrap(chunk2)
                ));

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, truncatedFlag, 512);

        // when
        StepVerifier.create(decorator.getBody().then()).verifyComplete();

        // then — chunk1(300) + 212 from chunk2 = 512 exactly
        assertThat(outputStream.size()).isEqualTo(512);
        assertThat(truncatedFlag.get()).isTrue();
    }

    @Test
    @DisplayName("빈 본문일 때 ByteArrayOutputStream에 0 bytes가 기록된다")
    void requestDecorator_emptyBody_writesZeroBytes() {
        // given
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/test")
                .body(Flux.empty());

        BodyCapturingRequestDecorator decorator =
                new BodyCapturingRequestDecorator(request, outputStream, new AtomicBoolean(), 1024 * 1024);

        // when
        StepVerifier.create(decorator.getBody().then())
                .verifyComplete();

        // then
        assertThat(outputStream.size()).isZero();
    }
}
