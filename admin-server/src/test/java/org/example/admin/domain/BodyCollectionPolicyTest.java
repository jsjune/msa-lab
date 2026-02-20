package org.example.admin.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BodyCollectionPolicyTest {

    @Test
    @DisplayName("필드 보유 확인 — id, pathPattern, enabled, createdAt, updatedAt 접근 가능")
    void fieldsAccessible() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .build();

        assertThat(policy.getPathPattern()).isEqualTo("/server-a/**");
        assertThat(policy.getCreatedAt()).isNotNull();
        assertThat(policy.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("기본값 — enabled = false")
    void defaultEnabled_isFalse() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/test/**")
                .build();

        assertThat(policy.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toggleEnabled — enabled 상태 반전 및 updatedAt 갱신")
    void toggleEnabled() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/test/**")
                .build();

        assertThat(policy.isEnabled()).isFalse();

        policy.toggleEnabled();

        assertThat(policy.isEnabled()).isTrue();

        policy.toggleEnabled();

        assertThat(policy.isEnabled()).isFalse();
    }
}