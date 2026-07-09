package com.julius.clipper.config;

import com.julius.clipper.config.properties.QueueProperties;
import com.julius.clipper.pipeline.DbQueue;
import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.RedisQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class QueueConfig {

    private final QueueProperties queueProperties;

    public QueueConfig(QueueProperties queueProperties) {
        this.queueProperties = queueProperties;
    }

    @Bean
    @Primary
    public QueueProvider queueProvider(RedisQueue redisQueue, DbQueue dbQueue) {
        if ("redis".equalsIgnoreCase(queueProperties.type())) {
            return redisQueue;
        }
        return dbQueue;
    }
}
