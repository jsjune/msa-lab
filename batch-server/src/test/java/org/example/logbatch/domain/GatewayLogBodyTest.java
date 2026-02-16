package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayLogBody - body/header 컨텐츠 엔티티")
class GatewayLogBodyTest {

    @Test
    @DisplayName("4개 TEXT 필드(requestBody, responseBody, requestHeaders, responseHeaders)를 보유한다")
    void hasAllContentFields() {
        // given / when
        GatewayLogBody body = GatewayLogBody.builder()
                .requestBody("{\"name\":\"test\"}")
                .responseBody("{\"result\":\"ok\"}")
                .requestHeaders("{\"Content-Type\":\"application/json\"}")
                .responseHeaders("{\"X-Response-Id\":\"abc\"}")
                .build();

        // then
        assertThat(body.getRequestBody()).isEqualTo("{\"name\":\"test\"}");
        assertThat(body.getResponseBody()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(body.getRequestHeaders()).isEqualTo("{\"Content-Type\":\"application/json\"}");
        assertThat(body.getResponseHeaders()).isEqualTo("{\"X-Response-Id\":\"abc\"}");
    }

    @Test
    @DisplayName("4개 필드 모두 nullable이다")
    void allFieldsAreNullable() {
        // given / when
        GatewayLogBody body = GatewayLogBody.builder().build();

        // then
        assertThat(body.getRequestBody()).isNull();
        assertThat(body.getResponseBody()).isNull();
        assertThat(body.getRequestHeaders()).isNull();
        assertThat(body.getResponseHeaders()).isNull();
    }

    @Test
    @DisplayName("GatewayLog과 1:1 관계(FK)를 가진다")
    void hasGatewayLogRelation() {
        // given
        GatewayLog log = GatewayLog.builder()
                .txId("tx-123")
                .hop(1)
                .build();

        // when
        GatewayLogBody body = GatewayLogBody.builder()
                .gatewayLog(log)
                .requestBody("body-data")
                .build();

        // then
        assertThat(body.getGatewayLog()).isSameAs(log);
        assertThat(body.getGatewayLog().getTxId()).isEqualTo("tx-123");
    }
}
