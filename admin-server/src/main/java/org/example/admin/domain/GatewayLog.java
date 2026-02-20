package org.example.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "gateway_log", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tx_id", "hop"})
})
public class GatewayLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false, length = 64)
    private String txId;

    @Column(name = "hop", nullable = false)
    private int hop;

    @Column(name = "path", length = 512)
    private String path;

    @Column(name = "target", length = 512)
    private String target;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "status")
    private int status;

    @Column(name = "req_time")
    private Instant reqTime;

    @Column(name = "res_time")
    private Instant resTime;

    @Column(name = "body_url", length = 512)
    private String bodyUrl;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "partition_day", nullable = false)
    private int partitionDay;

    @OneToOne(mappedBy = "gatewayLog", fetch = FetchType.LAZY)
    private GatewayLogBody body;
}