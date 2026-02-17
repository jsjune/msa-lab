package org.example.logbatch.repository;

import org.example.logbatch.domain.BodyCollectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("BodyCollectionPolicyRepository - 정책 저장소")
class BodyCollectionPolicyRepositoryTest {

    @Autowired
    private BodyCollectionPolicyRepository repository;

    @Test
    @DisplayName("정책 저장 및 조회")
    void saveAndFind() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-a/**")
                .enabled(true)
                .build();

        BodyCollectionPolicy saved = repository.save(policy);

        Optional<BodyCollectionPolicy> found = repository.findByPathPattern("/server-a/**");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getPathPattern()).isEqualTo("/server-a/**");
        assertThat(found.get().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("path 패턴으로 활성화/비활성화 업데이트")
    void updateEnabledByPathPattern() {
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern("/server-b/**")
                .enabled(false)
                .build();
        repository.save(policy);

        BodyCollectionPolicy found = repository.findByPathPattern("/server-b/**").orElseThrow();
        found.updateEnabled(true);
        repository.save(found);

        BodyCollectionPolicy updated = repository.findByPathPattern("/server-b/**").orElseThrow();
        assertThat(updated.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("전체 활성화된 path 목록 조회")
    void findAllEnabled() {
        repository.save(BodyCollectionPolicy.builder().pathPattern("/server-a/**").enabled(true).build());
        repository.save(BodyCollectionPolicy.builder().pathPattern("/server-b/**").enabled(false).build());
        repository.save(BodyCollectionPolicy.builder().pathPattern("/server-c/**").enabled(true).build());

        List<BodyCollectionPolicy> enabledPolicies = repository.findByEnabledTrue();

        assertThat(enabledPolicies).hasSize(2);
        assertThat(enabledPolicies).extracting(BodyCollectionPolicy::getPathPattern)
                .containsExactlyInAnyOrder("/server-a/**", "/server-c/**");
    }
}
