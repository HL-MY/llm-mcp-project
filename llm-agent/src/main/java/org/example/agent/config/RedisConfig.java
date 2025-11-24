package org.example.agent.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.llm.dto.llm.LlmMessage; // <-- 确保导入 LlmMessage
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * 配置用于序列化 List<LlmMessage> 的 RedisTemplate (上下文历史)
     */
    @Bean
    public RedisTemplate<String, List<LlmMessage>> llmMessageRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, List<LlmMessage>> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Key 序列化器使用 String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 1. 配置 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule()); // 支持 Instant 等 Java 8 时间类型

        // 2. Value 序列化器使用 Jackson JSON 序列化 (高效 & 非弃用)
        // 必须使用 TypeFactory 来构造泛型类型 List<LlmMessage>，以避免编译和运行时错误。
        Jackson2JsonRedisSerializer<List<LlmMessage>> jsonSerializer = new Jackson2JsonRedisSerializer<>(
                objectMapper.getTypeFactory().constructCollectionType(List.class, LlmMessage.class)
        );

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}