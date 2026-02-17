package org.example.logbatch.storage;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import okhttp3.Headers;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioLogFetcher - MinIO 오브젝트 조회")
class MinioLogFetcherTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private MinioLogFetcher minioLogFetcher;

    private GetObjectResponse mockResponse(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new GetObjectResponse(
                Headers.of(),
                "test-bucket",
                "",
                "test-object",
                new ByteArrayInputStream(bytes)
        );
    }

    private ErrorResponseException noSuchKeyException() {
        ErrorResponse errorResponse = new ErrorResponse("NoSuchKey", "Object not found", "", "", "", "", "");
        return new ErrorResponseException(errorResponse,
                new Response.Builder()
                        .code(404)
                        .message("Not Found")
                        .protocol(Protocol.HTTP_1_1)
                        .request(new Request.Builder().url("http://localhost").build())
                        .build(),
                null);
    }

    // ── 4.1 단건 오브젝트 조회 ──

    @Test
    @DisplayName("존재하는 오브젝트 → 문자열 반환")
    void fetchObject_exists_returnsString() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mockResponse("{\"name\":\"test\"}"));

        String result = minioLogFetcher.fetchObject("bucket", "path/obj.req");

        assertThat(result).isEqualTo("{\"name\":\"test\"}");
    }

    @Test
    @DisplayName("존재하지 않는 오브젝트 → null 반환 (예외 전파 없음)")
    void fetchObject_notExists_returnsNull() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(noSuchKeyException());

        String result = minioLogFetcher.fetchObject("bucket", "path/obj.req");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("MinIO 연결 실패 → null 반환 (예외 전파 없음)")
    void fetchObject_connectionFailed_returnsNull() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = minioLogFetcher.fetchObject("bucket", "path/obj.req");

        assertThat(result).isNull();
    }

    // ── 4.2 bodyUrl 기반 4개 오브젝트 일괄 조회 ──

    @Test
    @DisplayName("정상 bodyUrl → req, res, req.header, res.header 4개 모두 조회 성공")
    void fetchAllByBodyUrl_allExist_returnsFourObjects() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mockResponse("req-body"))
                .thenReturn(mockResponse("res-body"))
                .thenReturn(mockResponse("req-headers"))
                .thenReturn(mockResponse("res-headers"));

        MinioLogFetcher.FetchResult result =
                minioLogFetcher.fetchAllByBodyUrl("s3://bucket/2026/02/17/tx-hop1");

        assertThat(result.requestBody()).isEqualTo("req-body");
        assertThat(result.responseBody()).isEqualTo("res-body");
        assertThat(result.requestHeaders()).isEqualTo("req-headers");
        assertThat(result.responseHeaders()).isEqualTo("res-headers");
    }

    @Test
    @DisplayName("body-less 요청 (.req 없음) → req=null, 나머지 3개 정상 반환")
    void fetchAllByBodyUrl_noReqBody_reqIsNull() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(noSuchKeyException())       // .req → not found
                .thenReturn(mockResponse("res-body"))   // .res
                .thenReturn(mockResponse("req-headers"))// .req.header
                .thenReturn(mockResponse("res-headers"));// .res.header

        MinioLogFetcher.FetchResult result =
                minioLogFetcher.fetchAllByBodyUrl("s3://bucket/2026/02/17/tx-hop1");

        assertThat(result.requestBody()).isNull();
        assertThat(result.responseBody()).isEqualTo("res-body");
        assertThat(result.requestHeaders()).isEqualTo("req-headers");
        assertThat(result.responseHeaders()).isEqualTo("res-headers");
    }

    @Test
    @DisplayName("모든 오브젝트 조회 실패 → 4개 필드 모두 null (에러 아님)")
    void fetchAllByBodyUrl_allFail_allNull() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(noSuchKeyException());

        MinioLogFetcher.FetchResult result =
                minioLogFetcher.fetchAllByBodyUrl("s3://bucket/2026/02/17/tx-hop1");

        assertThat(result.requestBody()).isNull();
        assertThat(result.responseBody()).isNull();
        assertThat(result.requestHeaders()).isNull();
        assertThat(result.responseHeaders()).isNull();
    }

    @Test
    @DisplayName("일부 오브젝트만 존재 → 존재하는 것만 값, 나머지 null")
    void fetchAllByBodyUrl_partial_mixedResult() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mockResponse("req-body"))   // .req
                .thenThrow(noSuchKeyException())        // .res → not found
                .thenReturn(mockResponse("req-headers"))// .req.header
                .thenThrow(noSuchKeyException());       // .res.header → not found

        MinioLogFetcher.FetchResult result =
                minioLogFetcher.fetchAllByBodyUrl("s3://bucket/2026/02/17/tx-hop1");

        assertThat(result.requestBody()).isEqualTo("req-body");
        assertThat(result.responseBody()).isNull();
        assertThat(result.requestHeaders()).isEqualTo("req-headers");
        assertThat(result.responseHeaders()).isNull();
    }

    // ── 4.3 bodyUrl이 null인 경우 ──

    @Test
    @DisplayName("bodyUrl이 null → 4개 필드 모두 null, MinIO 호출 없음")
    void fetchAllByBodyUrl_nullUrl_noMinioCalls() throws Exception {
        MinioLogFetcher.FetchResult result = minioLogFetcher.fetchAllByBodyUrl(null);

        assertThat(result.requestBody()).isNull();
        assertThat(result.responseBody()).isNull();
        assertThat(result.requestHeaders()).isNull();
        assertThat(result.responseHeaders()).isNull();

        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }
}
