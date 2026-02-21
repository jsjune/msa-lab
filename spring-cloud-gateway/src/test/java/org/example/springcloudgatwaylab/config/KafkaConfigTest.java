package org.example.springcloudgatwaylab.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.example.springcloudgatwaylab.service.KafkaMetadataSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("KafkaConfig - Kafka 프로듀서 및 토픽 설정 검증")
class KafkaConfigTest {

    private KafkaConfig kafkaConfig;

    @BeforeEach
    void setUp() {
        kafkaConfig = new KafkaConfig();
        ReflectionTestUtils.setField(kafkaConfig, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(kafkaConfig, "metadataTopic", "gateway-meta-logs");
    }

    @Test
    @DisplayName("producerFactory: acks=1이 설정되어 Leader 기록 확인 후 응답한다")
    void producerFactory_acksIs1() {
        // when
        DefaultKafkaProducerFactory<String, String> factory =
                (DefaultKafkaProducerFactory<String, String>) kafkaConfig.producerFactory();

        // then
        Map<String, Object> props = factory.getConfigurationProperties();
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("1");
    }

    @Test
    @DisplayName("producerFactory: retries=0, linger.ms=10이 설정되어 있다")
    void producerFactory_retriesAndLingerConfigured() {
        // when
        DefaultKafkaProducerFactory<String, String> factory =
                (DefaultKafkaProducerFactory<String, String>) kafkaConfig.producerFactory();
        Map<String, Object> props = factory.getConfigurationProperties();

        // then
        assertThat(props.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(0);
        assertThat(props.get(ProducerConfig.LINGER_MS_CONFIG)).isEqualTo(10);
    }

    @Test
    @DisplayName("metadataTopic: cleanup.policy=delete가 설정되어 있다")
    void metadataTopic_cleanupPolicyIsDelete() {
        // when
        NewTopic topic = kafkaConfig.metadataTopic();

        // then
        assertThat(topic.configs()).containsEntry("cleanup.policy", "delete");
    }

    @Test
    @DisplayName("metadataTopic: retention.ms=604800000 (7일)이 설정되어 있다")
    void metadataTopic_retentionMs7Days() {
        // when
        NewTopic topic = kafkaConfig.metadataTopic();

        // then
        assertThat(topic.configs()).containsEntry("retention.ms", "604800000");
    }

    @Test
    @DisplayName("metadataTopic: 파티션 3개, 복제본 2개로 생성된다")
    void metadataTopic_partitionsAndReplicas() {
        // when
        NewTopic topic = kafkaConfig.metadataTopic();

        // then
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("metadataTopic: compact 정책이 아닌 delete 정책을 사용한다 (이전 hop 메타데이터 소실 방지)")
    void metadataTopic_notCompactPolicy() {
        // when
        NewTopic topic = kafkaConfig.metadataTopic();

        // then
        assertThat(topic.configs()).doesNotContainEntry("cleanup.policy", "compact");
    }

    @Test
    @DisplayName("KafkaConfig과 KafkaMetadataSender는 동일한 토픽 이름을 사용한다")
    void kafkaConfigAndSender_useSameTopicName() {
        // given — gateway.kafka.topic.metadata 프로퍼티 값이 두 빈에 모두 주입됨을 시뮬레이션
        String sharedTopicName = "custom-gateway-topic";
        ReflectionTestUtils.setField(kafkaConfig, "metadataTopic", sharedTopicName);

        // KafkaConfig가 생성하는 NewTopic 이름 확인
        NewTopic newTopic = kafkaConfig.metadataTopic();
        assertThat(newTopic.name()).isEqualTo(sharedTopicName);

        // KafkaMetadataSender도 동일한 토픽 이름으로 생성되는지 확인
        @SuppressWarnings("unchecked")
        KafkaMetadataSender sender = new KafkaMetadataSender(mock(KafkaTemplate.class), sharedTopicName);
        String senderTopic = (String) ReflectionTestUtils.getField(sender, "topic");
        assertThat(senderTopic).isEqualTo(newTopic.name());
    }
}
