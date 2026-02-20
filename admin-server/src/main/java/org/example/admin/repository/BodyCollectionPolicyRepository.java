package org.example.admin.repository;

import org.example.admin.domain.BodyCollectionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BodyCollectionPolicyRepository extends JpaRepository<BodyCollectionPolicy, Long> {

    Optional<BodyCollectionPolicy> findByPathPattern(String pathPattern);

    boolean existsByPathPattern(String pathPattern);
}