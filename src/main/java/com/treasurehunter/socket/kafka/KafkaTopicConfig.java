package com.treasurehunter.socket.kafka;

import com.TreasureHunter.CommonLib.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topic do socket-service gửi. Spring tự tạo khi khởi động nếu Kafka chưa có. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic characterPositionTopic() {
        // 3 partition: tối đa 3 consumer của character-service xử lý song song.
        return TopicBuilder.name(KafkaTopics.CHARACTER_POSITION_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
