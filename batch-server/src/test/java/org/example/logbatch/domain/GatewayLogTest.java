package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayLog - 메타데이터 엔티티")
class GatewayLogTest {

    @Test
    @DisplayName("필수 필드(txId, hop, path, status, reqTime, resTime, bodyUrl)를 보유한다")
    void hasRequiredFields() {
        // given
        Instant now = Instant.now();

        // when
        GatewayLog log = GatewayLog.builder()
                .txId("tx-123")
                .hop(1)
                .path("/server-a/hello")
                .status(200)
                .reqTime(now)
                .resTime(now.plusMillis(45))
                .bodyUrl("s3://bucket/2026/02/17/tx-123-hop1")
                .build();

        // then
        assertThat(log.getTxId()).isEqualTo("tx-123");
        assertThat(log.getHop()).isEqualTo(1);
        assertThat(log.getPath()).isEqualTo("/server-a/hello");
        assertThat(log.getStatus()).isEqualTo(200);
        assertThat(log.getReqTime()).isEqualTo(now);
        assertThat(log.getResTime()).isEqualTo(now.plusMillis(45));
        assertThat(log.getBodyUrl()).isEqualTo("s3://bucket/2026/02/17/tx-123-hop1");
    }

    @Test
    @DisplayName("선택 필드(target, durationMs, error)는 nullable이다")
    void optionalFieldsAreNullable() {
        // given / when
        GatewayLog log = GatewayLog.builder()
                .txId("tx-456")
                .hop(1)
                .build();

        // then — 선택 필드 미설정 시 null
        assertThat(log.getTarget()).isNull();
        assertThat(log.getDurationMs()).isNull();
        assertThat(log.getError()).isNull();
    }

    @Test
    @DisplayName("partitionDay 필드(1~31)를 보유한다")
    void hasPartitionDayField() {
        // given / when
        GatewayLog log = GatewayLog.builder()
                .partitionDay(17)
                .build();

        // then
        assertThat(log.getPartitionDay()).isEqualTo(17);
    }

    @Test
    @DisplayName("txId + hop 조합으로 논리적 고유 식별이 가능하다")
    void txIdAndHopFormLogicalIdentity() {
        // given
        GatewayLog log1 = GatewayLog.builder().txId("tx-same").hop(1).build();
        GatewayLog log2 = GatewayLog.builder().txId("tx-same").hop(1).build();
        GatewayLog log3 = GatewayLog.builder().txId("tx-same").hop(2).build();

        // then — 같은 txId+hop은 논리적으로 동일, 다른 hop은 다름
        assertThat(log1.getTxId()).isEqualTo(log2.getTxId());
        assertThat(log1.getHop()).isEqualTo(log2.getHop());
        assertThat(log1.getHop()).isNotEqualTo(log3.getHop());
    }
}