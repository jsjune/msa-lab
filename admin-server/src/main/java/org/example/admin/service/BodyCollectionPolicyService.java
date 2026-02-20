package org.example.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.BodyCollectionPolicy;
import org.example.admin.domain.PathPatternValidator;
import org.example.admin.repository.BodyCollectionPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BodyCollectionPolicyService {

    private final BodyCollectionPolicyRepository policyRepository;

    public List<BodyCollectionPolicy> findAll() {
        return policyRepository.findAll();
    }

    @Transactional
    public BodyCollectionPolicy create(String pathPattern) {
        if (!PathPatternValidator.isValid(pathPattern)) {
            throw new IllegalArgumentException("Invalid path pattern: " + pathPattern);
        }
        if (policyRepository.existsByPathPattern(pathPattern)) {
            throw new DuplicatePolicyException("Path pattern already exists: " + pathPattern);
        }
        BodyCollectionPolicy policy = BodyCollectionPolicy.builder()
                .pathPattern(pathPattern)
                .enabled(false)
                .build();
        return policyRepository.save(policy);
    }

    @Transactional
    public BodyCollectionPolicy toggle(Long id) {
        BodyCollectionPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        policy.toggleEnabled();
        return policy;
    }

    @Transactional
    public void delete(Long id) {
        if (!policyRepository.existsById(id)) {
            throw new PolicyNotFoundException("Policy not found: " + id);
        }
        policyRepository.deleteById(id);
    }
}