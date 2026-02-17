package org.example.logbatch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchProperties - 배치 설정값")
class BatchPropertiesTest {

    @Test
    @DisplayName("metadata 기본값: enabled=true, topic=gateway-meta-logs")
    void metadata_defaultValues() {
        BatchProperties.MetadataProperties metadata =
                new BatchProperties.MetadataProperties(true, "gateway-meta-logs");

        assertThat(metadata.isEnabled()).isTrue();
        assertThat(metadata.getTopic()).isEqualTo("gateway-meta-logs");
    }

    @Test
    @DisplayName("body 기본값: enabled=true, fixedDelay=30000ms, batchSize=100")
    void body_defaultValues() {
        BatchProperties.BodyProperties body =
                new BatchProperties.BodyProperties(true, 30000, 100);

        assertThat(body.isEnabled()).isTrue();
        assertThat(body.getFixedDelay()).isEqualTo(30000L);
        assertThat(body.getBatchSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("metadata.enabled=false로 설정 가능")
    void metadata_canBeDisabled() {
        BatchProperties.MetadataProperties metadata =
                new BatchProperties.MetadataProperties(false, "gateway-meta-logs");

        assertThat(metadata.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("body.enabled=false로 설정 가능")
    void body_canBeDisabled() {
        BatchProperties.BodyProperties body =
                new BatchProperties.BodyProperties(false, 30000, 100);

        assertThat(body.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("metadata.topic 커스텀 값 설정 가능")
    void metadata_customTopic() {
        BatchProperties.MetadataProperties metadata =
                new BatchProperties.MetadataProperties(true, "custom-topic");

        assertThat(metadata.getTopic()).isEqualTo("custom-topic");
    }

    @Test
    @DisplayName("nested 구조로 metadata, body 각각 접근 가능")
    void nestedStructure_accessible() {
        BatchProperties props = createDefaultProperties();

        assertThat(props.getMetadata()).isNotNull();
        assertThat(props.getBody()).isNotNull();
        assertThat(props.getMetadata().isEnabled()).isTrue();
        assertThat(props.getBody().isEnabled()).isTrue();
    }

    private BatchProperties createDefaultProperties() {
        return new BatchProperties(
                new BatchProperties.MetadataProperties(true, "gateway-meta-logs"),
                new BatchProperties.BodyProperties(true, 30000, 100));
    }
}
