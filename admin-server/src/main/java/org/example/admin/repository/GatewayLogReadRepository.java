package org.example.admin.repository;

import org.example.admin.domain.GatewayLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface GatewayLogReadRepository extends JpaRepository<GatewayLog, Long> {

    // 3.1 통계 — path별 요청 수, 에러 수
    @Query("""
            SELECT g.path AS path,
                   COUNT(g) AS count,
                   SUM(CASE WHEN g.status >= 400 THEN 1 ELSE 0 END) AS errorCount
            FROM GatewayLog g
            WHERE g.reqTime BETWEEN :from AND :to
            GROUP BY g.path
            """)
    List<PathStatsProjection> findPathStats(@Param("from") Instant from, @Param("to") Instant to);

    // 3.2 통계 — path별 duration 리스트 (백분위 계산용)
    @Query("""
            SELECT g.durationMs FROM GatewayLog g
            WHERE g.path = :path AND g.reqTime BETWEEN :from AND :to AND g.durationMs IS NOT NULL
            ORDER BY g.durationMs
            """)
    List<Long> findDurationsByPath(@Param("path") String path,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    // 3.3 분산추적 — txId로 hop 조회
    @Query("SELECT g FROM GatewayLog g WHERE g.txId = :txId ORDER BY g.hop")
    List<GatewayLog> findByTxIdOrderByHop(@Param("txId") String txId);

    // 3.3 분산추적 — txId로 body 포함 조회
    @Query("SELECT g FROM GatewayLog g LEFT JOIN FETCH g.body WHERE g.txId = :txId ORDER BY g.hop")
    List<GatewayLog> findByTxIdWithBody(@Param("txId") String txId);

    // 3.4 분산추적 검색 — 기간 내 txId 목록 (페이징)
    @Query(value = "SELECT DISTINCT g.txId FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to ORDER BY g.txId",
           countQuery = "SELECT COUNT(DISTINCT g.txId) FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to")
    Page<String> findDistinctTxIds(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    // 3.4 분산추적 검색 — path 필터
    @Query(value = "SELECT DISTINCT g.txId FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to AND g.path = :path ORDER BY g.txId",
           countQuery = "SELECT COUNT(DISTINCT g.txId) FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to AND g.path = :path")
    Page<String> findDistinctTxIdsByPath(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("path") String path, Pageable pageable);

    // 3.4 분산추적 검색 — 에러만 (status >= 400)
    @Query(value = "SELECT DISTINCT g.txId FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to AND g.status >= 400 ORDER BY g.txId",
           countQuery = "SELECT COUNT(DISTINCT g.txId) FROM GatewayLog g WHERE g.reqTime BETWEEN :from AND :to AND g.status >= 400")
    Page<String> findDistinctTxIdsByError(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}