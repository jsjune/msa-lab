package org.example.logbatch.service;

import lombok.RequiredArgsConstructor;
import org.example.logbatch.domain.BodyCollectionPolicy;
import org.example.logbatch.repository.BodyCollectionPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BodyCollectionService {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final BodyCollectionPolicyRepository policyRepository;

    private List<BodyCollectionPolicy> cachedPolicies;

    public boolean shouldCollectBody(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        List<BodyCollectionPolicy> policies = getEnabledPolicies();

        return policies.stream()
                .anyMatch(policy -> PATH_MATCHER.match(policy.getPathPattern(), path));
    }

    /**
     * 배치 시작 전 호출하여 캐시를 갱신한다.
     * 배치 단위로 한 번만 DB 조회하고, 이후 shouldCollectBody는 캐시를 사용한다.
     */
    public void refreshPolicyCache() {
        this.cachedPolicies = policyRepository.findByEnabledTrue();
    }

    private List<BodyCollectionPolicy> getEnabledPolicies() {
        if (cachedPolicies != null) {
            return cachedPolicies;
        }
        return policyRepository.findByEnabledTrue();
    }

    public void clearPolicyCache() {
        this.cachedPolicies = null;
    }
}
