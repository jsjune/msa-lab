package org.example.admin.repository;

import java.time.Instant;

public interface TraceSummaryProjection {
    String getTxId();
    Instant getReqTime();
}
