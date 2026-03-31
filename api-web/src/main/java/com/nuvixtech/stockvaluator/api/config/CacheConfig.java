package com.nuvixtech.stockvaluator.api.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();

        // Cache de valuaciones: TTL 24h (datos financieros no cambian frecuentemente)
        manager.registerCustomCache("valuations",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .build());

        // Cache de watchlist: TTL 5 min (refleja cambios de precios con más frecuencia)
        manager.registerCustomCache("watchlist",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .build());

        return manager;
    }
}
