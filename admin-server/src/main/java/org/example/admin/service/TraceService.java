package org.example.admin.service;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.DateRange;
import org.example.admin.domain.GatewayLog;
import org.example.admin.domain.TraceDetail;
import org.example.admin.domain.TraceHop;
import org.example.admin.repository.GatewayLogReadRepository;
import org.example.admin.repository.TraceSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TraceService {

    private final GatewayLogReadRepository logRepository;

    public TraceDetail getTrace(String txId, boolean includeDetail) {
        List<GatewayLog> logs = includeDetail
                ? logRepository.findByTxIdWithBody(txId)
                : logRepository.findByTxIdOrderByHop(txId);

        List<TraceHop> hops = logs.stream()
                .map(log -> toTraceHop(log, includeDetail))
                .toList();

        return new TraceDetail(txId, hops);
    }

    public Page<TraceSummaryProjection> searchTraces(DateRange range, String path, String status, Pageable pageable) {
        if (path != null && !path.isBlank()) {
            return logRepository.findDistinctTxIdsByPath(range.getFrom(), range.getTo(), path, pageable);
        }
        if ("error".equalsIgnoreCase(status) || "5xx".equalsIgnoreCase(status) || "4xx".equalsIgnoreCase(status)) {
            return logRepository.findDistinctTxIdsByError(range.getFrom(), range.getTo(), pageable);
        }
        return logRepository.findDistinctTxIds(range.getFrom(), range.getTo(), pageable);
    }

    private TraceHop toTraceHop(GatewayLog log, boolean includeDetail) {
        TraceHop.TraceHopBuilder builder = TraceHop.builder()
                .txId(log.getTxId())
                .hop(log.getHop())
                .path(log.getPath())
                .target(log.getTarget())
                .status(log.getStatus())
                .durationMs(log.getDurationMs())
                .reqTime(log.getReqTime())
                .resTime(log.getResTime())
                .error(log.getError());

        if (includeDetail && log.getBody() != null) {
            builder.requestHeaders(log.getBody().getRequestHeaders())
                    .requestBody(log.getBody().getRequestBody())
                    .responseHeaders(log.getBody().getResponseHeaders())
                    .responseBody(log.getBody().getResponseBody());
        }

        return builder.build();
    }
}
