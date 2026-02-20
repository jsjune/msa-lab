package org.example.logbatch.repository;

import org.example.logbatch.domain.GatewayLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface GatewayLogRepository extends JpaRepository<GatewayLog, Long> {

    List<GatewayLog> findByTxIdOrderByHopAsc(String txId);

    List<GatewayLog> findByReqTimeBetweenOrderByReqTimeAsc(Instant from, Instant to);

    List<GatewayLog> findByStatusBetween(int from, int to);

    @Query("SELECT g FROM GatewayLog g LEFT JOIN FETCH g.body WHERE g.txId = :txId ORDER BY g.hop ASC")
    List<GatewayLog> findByTxIdWithBody(@Param("txId") String txId);

    @Query("SELECT g FROM GatewayLog g WHERE g.bodyUrl IS NOT NULL AND g.body IS NULL AND g.bodyRetryCount < :maxRetries")
    List<GatewayLog> findLogsNeedingBodyCollection(@Param("maxRetries") int maxRetries, Pageable pageable);
}
