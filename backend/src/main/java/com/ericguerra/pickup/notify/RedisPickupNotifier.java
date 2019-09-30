package com.ericguerra.pickup.notify;

import com.ericguerra.pickup.domain.PickupOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Publishes to {@code store:<id>:orders} (the associate board subscribes over SSE) and keeps a
 * per-store hash of open-order snapshots so a freshly opened board renders without hitting MySQL.
 * Customer messages go onto a list drained by the SMS worker.
 */
@Component
@Profile("!test")
public class RedisPickupNotifier implements PickupNotifier {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisPickupNotifier(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void orderChanged(PickupOrder order) {
        String payload = write(order);
        String boardKey = "store:" + order.getStoreId() + ":board";
        if (order.getStatus().isOpen()) redis.opsForHash().put(boardKey, order.getOrderNo(), payload);
        else redis.opsForHash().delete(boardKey, order.getOrderNo());
        redis.expire(boardKey, Duration.ofDays(4));
        redis.convertAndSend("store:" + order.getStoreId() + ":orders", payload);
    }

    @Override
    public void customerMessage(PickupOrder order, String message) {
        if (order.getCustomerPhone() == null) return;
        redis.opsForList().leftPush("sms:outbox", write(Map.of("to", order.getCustomerPhone(), "order", order.getOrderNo(), "body", message)));
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
