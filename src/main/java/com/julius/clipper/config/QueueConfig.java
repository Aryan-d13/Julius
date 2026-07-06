package com.julius.clipper.config;

import com.julius.clipper.pipeline.DbQueue;
import com.julius.clipper.pipeline.QueueProvider;
import com.julius.clipper.pipeline.RedisQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class QueueConfig {

    @Value("${clipper.queue.type:db}")
    private String queueType;

    @Bean
    @Primary
    public QueueProvider queueProvider(RedisQueue redisQueue, DbQueue dbQueue) {
        if ("redis".equalsIgnoreCase(queueType)) {
            return redisQueue;
        }
        return dbQueue;
    }
}
