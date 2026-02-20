package org.example.admin.repository;

public interface PathStatsProjection {
    String getPath();
    long getCount();
    long getErrorCount();
}