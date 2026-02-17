package org.example.logbatch.service;

import org.example.logbatch.domain.BodyCollectionPolicy;
import org.example.logbatch.repository.BodyCollectionPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BodyCollectionService - body 수집 정책 판단")
class BodyCollectionServiceTest {

    @Mock
    private BodyCollectionPolicyRepository policyRepository;

    @InjectMocks
    private BodyCollectionService bodyCollectionService;

    // ── 2.1 와일드카드 & 정확한 매칭 ──

    @Test
    @DisplayName("와일드카드 패턴 매칭 (/server-a/**) → /server-a/hello 매치 확인")
    void wildcardPattern_matchesSubPath() {
        when(policyRepository.findByEnabledTrue()).thenReturn(List.of(
                BodyCollectionPolicy.builder().pathPattern("/server-a/**").enabled(true).build()
        ));

        assertThat(bodyCollectionService.shouldCollectBody("/server-a/hello")).isTrue();
        assertThat(bodyCollectionService.shouldCollectBody("/server-a/data/123")).isTrue();
    }

    @Test
    @DisplayName("정확한 path 매칭 (/server-a/data) → 일치하는 경우만 매치")
    void exactPattern_matchesOnlyExact() {
        when(policyRepository.findByEnabledTrue()).thenReturn(List.of(
                BodyCollectionPolicy.builder().pathPattern("/server-a/data").enabled(true).build()
        ));

        assertThat(bodyCollectionService.shouldCollectBody("/server-a/data")).isTrue();
        assertThat(bodyCollectionService.shouldCollectBody("/server-a/data/123")).isFalse();
        assertThat(bodyCollectionService.shouldCollectBody("/server-a/hello")).isFalse();
    }

    // ── 2.2 정책 판단 (shouldCollectBody) ──

    @Test
    @DisplayName("활성화된 path 요청 → true 반환")
    void enabledPath_returnsTrue() {
        when(policyRepository.findByEnabledTrue()).thenReturn(List.of(
                BodyCollectionPolicy.builder().pathPattern("/server-b/**").enabled(true).build()
        ));

        assertThat(bodyCollectionService.shouldCollectBody("/server-b/chain")).isTrue();
    }

    @Test
    @DisplayName("비활성화된 path 요청 → false 반환")
    void disabledPath_returnsFalse() {
        // findByEnabledTrue returns empty because the policy is disabled
        when(policyRepository.findByEnabledTrue()).thenReturn(Collections.emptyList());

        assertThat(bodyCollectionService.shouldCollectBody("/server-a/hello")).isFalse();
    }

    @Test
    @DisplayName("등록되지 않은 path → false 반환 (기본 비활성화)")
    void unregisteredPath_returnsFalse() {
        when(policyRepository.findByEnabledTrue()).thenReturn(List.of(
                BodyCollectionPolicy.builder().pathPattern("/server-a/**").enabled(true).build()
        ));

        assertThat(bodyCollectionService.shouldCollectBody("/server-c/unknown")).isFalse();
    }

    @Test
    @DisplayName("빈 정책 (아무것도 등록 안 됨) → 항상 false")
    void emptyPolicies_alwaysFalse() {
        when(policyRepository.findByEnabledTrue()).thenReturn(Collections.emptyList());

        assertThat(bodyCollectionService.shouldCollectBody("/server-a/hello")).isFalse();
        assertThat(bodyCollectionService.shouldCollectBody("/any/path")).isFalse();
    }
}
