package org.example.admin.repository;

import org.example.admin.domain.BodyCollectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BodyCollectionPolicyRepositoryTest {

    @Autowired
    private BodyCollectionPolicyRepository repository;

    @Test
    @DisplayName("정책 저장/조회/수정/삭제 기본 CRUD")
    void basicCrud() {
        // 저장
        BodyCollectionPolicy saved = repository.save(
                BodyCollectionPolicy.builder().pathPattern("/server-a/**").build());
        assertThat(saved.getId()).isNotNull();

        // 조회
        Optional<BodyCollectionPolicy> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getPathPattern()).isEqualTo("/server-a/**");

        // 수정
        found.get().toggleEnabled();
        repository.flush();
        BodyCollectionPolicy updated = repository.findById(saved.getId()).orElseThrow();
        assertThat(updated.isEnabled()).isTrue();

        // 삭제
        repository.deleteById(saved.getId());
        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("enabled = true인 정책만 조회")
    void existsByPathPattern() {
        repository.save(BodyCollectionPolicy.builder().pathPattern("/a/**").build());

        assertThat(repository.existsByPathPattern("/a/**")).isTrue();
        assertThat(repository.existsByPathPattern("/b/**")).isFalse();
    }

    @Test
    @DisplayName("pathPattern으로 단건 조회")
    void findByPathPattern() {
        repository.save(BodyCollectionPolicy.builder().pathPattern("/server-c/**").build());

        Optional<BodyCollectionPolicy> found = repository.findByPathPattern("/server-c/**");

        assertThat(found).isPresent();
        assertThat(found.get().getPathPattern()).isEqualTo("/server-c/**");
    }
}