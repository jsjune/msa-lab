package org.example.springcloudgatwaylab.service;

import io.minio.MinioAsyncClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("MinioStorageService - 오브젝트 저장 및 URL 생성")
class MinioStorageServiceTest {

    private MinioAsyncClient minioClient;
    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        minioClient = mock(MinioAsyncClient.class);
        service = new MinioStorageService(minioClient, "test-bucket");
    }

    @Test
    @DisplayName("업로드 시 날짜 파티션 기반 오브젝트 이름을 생성한다")
    void upload_objectName_followsDatePartitionedPattern() throws Exception {
        // given
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(ObjectWriteResponse.class)));

        String txId = "abc-123";
        String type = "req";
        int hop = 1;
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // when
        service.upload(txId, new byte[]{1, 2, 3}, type, hop);

        // then
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo("test-bucket");
        assertThat(args.object()).isEqualTo(today + "/abc-123-hop1.req");
    }

    @Test
    @DisplayName("업로드 시 stream size와 content-type을 올바르게 설정한다")
    void upload_setsStreamSizeAndContentType() throws Exception {
        // given
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(ObjectWriteResponse.class)));
        byte[] data = new byte[]{10, 20, 30, 40, 50};

        // when
        service.upload("tx1", data, "res", 1);

        // then
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());

        PutObjectArgs args = captor.getValue();
        assertThat(args.objectSize()).isEqualTo(5L);
        assertThat(args.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("모든 type(req, res, req.header, res.header)에 대해 올바른 오브젝트 이름을 생성한다")
    void upload_objectName_includesCorrectTypeForAllTypes() throws Exception {
        // given
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(ObjectWriteResponse.class)));
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // when & then — verify each type suffix
        for (String type : new String[]{"req", "res", "req.header", "res.header"}) {
            service.upload("tx1", new byte[]{1}, type, 2);
        }

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(4)).putObject(captor.capture());

        assertThat(captor.getAllValues().stream().map(PutObjectArgs::object))
                .containsExactly(
                        today + "/tx1-hop2.req",
                        today + "/tx1-hop2.res",
                        today + "/tx1-hop2.req.header",
                        today + "/tx1-hop2.res.header"
                );
    }

    @Test
    @DisplayName("MinIO putObject 비동기 실패 시 예외를 전파하지 않는다")
    void upload_asyncFailure_doesNotPropagate() throws Exception {
        // given
        CompletableFuture<ObjectWriteResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("MinIO connection refused"));
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(failedFuture);

        // when & then — no exception thrown
        service.upload("tx1", new byte[]{1, 2, 3}, "req", 1);
    }

    @Test
    @DisplayName("MinIO putObject 동기 예외 시 예외를 전파하지 않는다")
    void upload_syncException_doesNotPropagate() throws Exception {
        // given
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("Sync error"));

        // when & then — no exception thrown
        service.upload("tx1", new byte[]{1, 2, 3}, "req", 1);
    }

    @Test
    @DisplayName("S3 스타일 URL(s3://bucket/datePath/txId-hopN)을 반환한다")
    void getStorageBaseUrl_returnsS3StyleUrl() {
        // given
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // when
        String url = service.getStorageBaseUrl("tx-abc", 3);

        // then
        assertThat(url).isEqualTo("s3://test-bucket/" + today + "/tx-abc-hop3");
    }
}