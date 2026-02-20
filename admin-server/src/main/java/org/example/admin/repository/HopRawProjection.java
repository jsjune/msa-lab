package org.example.admin.repository;

public interface HopRawProjection {
    String getTxId();
    int getHop();
    String getPath();
    int getStatus();
    Long getDurationMs();
}
