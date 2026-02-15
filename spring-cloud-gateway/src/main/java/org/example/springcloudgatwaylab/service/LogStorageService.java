package org.example.springcloudgatwaylab.service;

public interface LogStorageService {
    /**
     * Uploads data directly to object storage.
     * @param hop the gateway hop count for this request
     */
    void upload(String txId, byte[] data, String type, int hop);

    /**
     * Returns the base URL or identifier for retrieval.
     * @param hop the gateway hop count for this request
     */
    String getStorageBaseUrl(String txId, int hop);
}
