package org.example.springcloudgatwaylab.filter;

import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Response body를 ByteArrayOutputStream에 복사한다.
 * maxBodySizeBytes 초과 시 버퍼를 truncate하고 truncatedFlag를 true로 설정한다.
 */
public class BodyCapturingResponseDecorator extends ServerHttpResponseDecorator {

    private final ByteArrayOutputStream outputStream;
    private final AtomicBoolean truncatedFlag;
    private final int maxBodySizeBytes;

    public BodyCapturingResponseDecorator(ServerHttpResponse delegate,
                                          ByteArrayOutputStream outputStream,
                                          AtomicBoolean truncatedFlag,
                                          int maxBodySizeBytes) {
        super(delegate);
        this.outputStream = outputStream;
        this.truncatedFlag = truncatedFlag;
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    @NotNull
    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return super.writeWith(Flux.from(body).doOnNext(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            buffer.readPosition(buffer.readPosition() - bytes.length);
            int currentSize = outputStream.size();
            if (currentSize < maxBodySizeBytes) {
                int toWrite = Math.min(bytes.length, maxBodySizeBytes - currentSize);
                outputStream.write(bytes, 0, toWrite);
                if (toWrite < bytes.length) {
                    truncatedFlag.set(true);
                }
            } else {
                truncatedFlag.set(true);
            }
        }));
    }
}
