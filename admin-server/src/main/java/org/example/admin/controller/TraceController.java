package org.example.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.admin.domain.DateRange;
import org.example.admin.domain.TraceDetail;
import org.example.admin.repository.TraceSummaryProjection;
import org.example.admin.service.TraceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestController
@RequestMapping("/api/traces")
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @GetMapping("/{txId}")
    public TraceDetail getTrace(
            @PathVariable String txId,
            @RequestParam(defaultValue = "false") boolean includeDetail) {
        TraceDetail detail = traceService.getTrace(txId, includeDetail);
        if (detail.getHopCount() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found: " + txId);
        }
        return detail;
    }

    @GetMapping
    public Page<TraceSummaryProjection> searchTraces(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        DateRange range = DateRange.of(from, to);
        return traceService.searchTraces(range, path, status, PageRequest.of(page, size));
    }
}
