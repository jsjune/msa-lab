package org.example.springcloudgatwaylab.filter;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Request body를 ByteArrayOutputStream에 복사한다.
 * maxBodySizeBytes 초과 시 버퍼를 truncate하고 truncatedFlag를 true로 설정한다.
 */
public class BodyCapturingRequestDecorator extends ServerHttpRequestDecorator {

    private final ByteArrayOutputStream outputStream;
    private final AtomicBoolean truncatedFlag;
    private final int maxBodySizeBytes;

    public BodyCapturingRequestDecorator(ServerHttpRequest delegate,
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
    public Flux<DataBuffer> getBody() {
        return super.getBody().doOnNext(buffer -> {
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
        });
    }
}
