package org.example.logbatch.repository;

import org.example.logbatch.domain.BodyCollectionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BodyCollectionPolicyRepository extends JpaRepository<BodyCollectionPolicy, Long> {

    Optional<BodyCollectionPolicy> findByPathPattern(String pathPattern);

    List<BodyCollectionPolicy> findByEnabledTrue();
}
