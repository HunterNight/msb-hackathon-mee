package com.app.config;

import com.app.constant.AppConstants;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis is a first-class component: rate limits, single-use tokens, caches, SSE fan-out and job
 * locks. Every key is prefixed {@code <service>:<purpose>:<id>} and every key has a TTL
 * (guideline 01 §7b).
 */
@Configuration
@EnableCaching
public class RedisConfig {

  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
    return new StringRedisTemplate(factory);
  }

  @Bean
  public RedisCacheConfiguration cacheConfiguration() {
    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofSeconds(AppConstants.DEFAULT_CACHE_TTL_SECONDS))
        .disableCachingNullValues()
        .prefixCacheNameWith(AppConstants.REDIS_PREFIX + ":cache:")
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));
  }
}
