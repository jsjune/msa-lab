package org.example.logbatch.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BodyCollectionPolicy - 엔티티 기본 동작")
class BodyCollectionPolicyTest {

    @Test
    @DisplayName("기본 정책 = 비활성화 (enabled=false)")
    void defaultPolicy_isDisabled() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .build();

        assertThat(policy.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("특정 path 패턴 활성화 → enabled=true로 설정 가능")
    void enablePolicy_setsEnabledTrue() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .enabled(true)
                .build();

        assertThat(policy.isEnabled()).isTrue();
        assertThat(policy.getPathPattern()).isEqualTo("/server-a/**");
    }

    @Test
    @DisplayName("updateEnabled로 활성화/비활성화 토글 가능")
    void updateEnabled_togglesState() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .build();

        assertThat(policy.isEnabled()).isFalse();

        policy.updateEnabled(true);
        assertThat(policy.isEnabled()).isTrue();

        policy.updateEnabled(false);
        assertThat(policy.isEnabled()).isFalse();
    }
}
