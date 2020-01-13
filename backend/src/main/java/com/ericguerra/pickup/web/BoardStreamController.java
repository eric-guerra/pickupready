package com.ericguerra.pickup.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fans Redis pub/sub out to associate tablets over SSE. Every app instance subscribes, so a tablet
 * connected to node A still sees an order staged through node B.
 */
@RestController
@Profile("!test")
public class BoardStreamController {
    private final Map<String, Set<SseEmitter>> byStore = new ConcurrentHashMap<>();

    public BoardStreamController(RedisMessageListenerContainer container) {
        container.addMessageListener((message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8); // store:<id>:orders
            String store = channel.split(":")[1];
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            for (SseEmitter e : byStore.getOrDefault(store, Set.of())) {
                try {
                    e.send(SseEmitter.event().data(body));
                } catch (IOException ex) {
                    e.complete();
                }
            }
        }, new PatternTopic("store:*:orders"));
    }

    @GetMapping("/api/stores/{store}/stream")
    public SseEmitter stream(@PathVariable String store) {
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> set = byStore.computeIfAbsent(store, k -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        Runnable remove = () -> set.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        return emitter;
    }

    @Configuration
    @Profile("!test")
    static class RedisListenerConfig {
        @Bean
        RedisMessageListenerContainer redisListenerContainer(RedisConnectionFactory cf) {
            RedisMessageListenerContainer c = new RedisMessageListenerContainer();
            c.setConnectionFactory(cf);
            return c;
        }
    }
}
