package org.example.logbatch.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@Getter
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

    private final MetadataProperties metadata;
    private final BodyProperties body;

    public BatchProperties(
            @DefaultValue MetadataProperties metadata,
            @DefaultValue BodyProperties body) {
        this.metadata = metadata;
        this.body = body;
    }

    @Getter
    public static class MetadataProperties {
        private final boolean enabled;
        private final String topic;

        public MetadataProperties(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("gateway-meta-logs") String topic) {
            this.enabled = enabled;
            this.topic = topic;
        }
    }

    @Getter
    public static class BodyProperties {
        private final boolean enabled;
        private final long fixedDelay;
        private final int batchSize;
        private final int maxRetries;

        public BodyProperties(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("30000") long fixedDelay,
                @DefaultValue("100") int batchSize,
                @DefaultValue("3") int maxRetries) {
            this.enabled = enabled;
            this.fixedDelay = fixedDelay;
            this.batchSize = batchSize;
            this.maxRetries = maxRetries;
        }
    }
}
