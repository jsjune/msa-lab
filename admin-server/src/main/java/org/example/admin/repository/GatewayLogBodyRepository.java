package org.example.admin.repository;

import org.example.admin.domain.GatewayLogBody;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayLogBodyRepository extends JpaRepository<GatewayLogBody, Long> {
}
