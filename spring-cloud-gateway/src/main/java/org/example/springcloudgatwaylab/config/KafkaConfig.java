package org.example.springcloudgatwaylab.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.topic.metadata:gateway-meta-logs}")
    private String metadataTopic;

    /**
     * 1. Producer Factory Configuration
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Performance & Reliability Settings (Speed > Reliability)
        configProps.put(ProducerConfig.ACKS_CONFIG, "0"); // Fire and forget
        configProps.put(ProducerConfig.RETRIES_CONFIG, 0);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Batching delay

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 2. Kafka Template (Used by Sender)
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 3. Topic Definition (Auto Creation)
     */
    @Bean
    public NewTopic metadataTopic() {
        return TopicBuilder.name(metadataTopic)
                .partitions(3)
                .replicas(2)
                .compact()
                .build();
    }
}
