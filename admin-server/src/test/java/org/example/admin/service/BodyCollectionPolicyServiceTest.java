package org.example.admin.service;

import org.example.admin.domain.BodyCollectionPolicy;
import org.example.admin.repository.BodyCollectionPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.data.domain.Sort;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BodyCollectionPolicyServiceTest {

    @Mock
    private BodyCollectionPolicyRepository policyRepository;

    @InjectMocks
    private BodyCollectionPolicyService policyService;

    @Test
    @DisplayName("정책 생성 — 새 path 패턴 등록, 기본 비활성화")
    void create_newPolicy() {
        given(policyRepository.existsByPathPattern("/server-a/**")).willReturn(false);
        given(policyRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        BodyCollectionPolicy policy = policyService.create("/server-a/**");

        assertThat(policy.getPathPattern()).isEqualTo("/server-a/**");
        assertThat(policy.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("정책 조회 — 전체 목록 반환")
    void findAll() {
        List<BodyCollectionPolicy> policies = List.of(
                BodyCollectionPolicy.builder().pathPattern("/a/**").build(),
                BodyCollectionPolicy.builder().pathPattern("/b/**").build()
        );
        given(policyRepository.findAll(any(Sort.class))).willReturn(policies);

        List<BodyCollectionPolicy> result = policyService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("활성화 토글 — enabled 상태 반전")
    void toggle() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .build();
        given(policyRepository.findById(1L)).willReturn(Optional.of(policy));

        BodyCollectionPolicy toggled = policyService.toggle(1L);

        assertThat(toggled.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("정책 삭제 — 존재하는 정책 삭제")
    void delete_existing() {
        given(policyRepository.existsById(1L)).willReturn(true);

        policyService.delete(1L);

        verify(policyRepository).deleteById(1L);
    }

    @Test
    @DisplayName("중복 pathPattern 등록 시 DuplicatePolicyException 발생")
    void create_duplicate_throws() {
        given(policyRepository.existsByPathPattern("/server-a/**")).willReturn(true);

        assertThatThrownBy(() -> policyService.create("/server-a/**"))
                .isInstanceOf(DuplicatePolicyException.class);
    }

    @Test
    @DisplayName("유효하지 않은 패턴 등록 시 IllegalArgumentException 발생")
    void create_invalidPattern_throws() {
        assertThatThrownBy(() -> policyService.create("server-a/**"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 정책 삭제 시 PolicyNotFoundException 발생")
    void delete_notFound_throws() {
        given(policyRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> policyService.delete(99L))
                .isInstanceOf(PolicyNotFoundException.class);
    }
}