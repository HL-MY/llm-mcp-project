package org.example.agent.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.llm.dto.llm.LlmMessage;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
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
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<List<LlmMessage>> jsonSerializer = new Jackson2JsonRedisSerializer<>(
                objectMapper.getTypeFactory().constructCollectionType(List.class, LlmMessage.class)
        );
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 【全面配置】针对 ToolService 中所有的缓存项设置过期时间
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // 1. 定义通用的 JSON 序列化器
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        GenericJackson2JsonRedisSerializer genericSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // 2. 定义【默认】配置 (兜底策略：1小时过期)
        // 如果以后你加了新的 @Cacheable 但忘了在这里配，它也会在 1 小时后自动清除，不会永久占用。
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericSerializer))
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();

        return (builder) -> builder
                .cacheDefaults(defaultCacheConfig) // 应用默认配置

                // --- 3. 针对不同业务设置具体过期时间 ---

                // 【天气】：30分钟 (变化频率中等)
                .withCacheConfiguration("weatherCache",
                        defaultCacheConfig.entryTtl(Duration.ofMinutes(30)))

                // 【油价】：1小时 (通常每天变动，1小时足够安全)
                .withCacheConfiguration("oilPriceCache",
                        defaultCacheConfig.entryTtl(Duration.ofHours(1)))

                // 【金价】：5分钟 (金融数据，波动较快)
                .withCacheConfiguration("goldPriceCache",
                        defaultCacheConfig.entryTtl(Duration.ofMinutes(5)))

                // 【汇率】：5分钟 (金融数据，波动较快)
                .withCacheConfiguration("exchangeRateCache",
                        defaultCacheConfig.entryTtl(Duration.ofMinutes(5)))

                // 【基金】：10分钟 (通常盘后更新，但为了防呆设置 10 分钟)
                .withCacheConfiguration("fundInfoCache",
                        defaultCacheConfig.entryTtl(Duration.ofMinutes(10)));
    }
}