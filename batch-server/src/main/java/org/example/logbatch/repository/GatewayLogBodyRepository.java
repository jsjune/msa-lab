package org.example.logbatch.repository;

import org.example.logbatch.domain.GatewayLogBody;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayLogBodyRepository extends JpaRepository<GatewayLogBody, Long> {
}
