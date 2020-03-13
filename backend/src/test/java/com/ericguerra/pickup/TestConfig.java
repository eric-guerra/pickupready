package com.ericguerra.pickup;

import com.ericguerra.pickup.domain.PickupOrder;
import com.ericguerra.pickup.notify.PickupNotifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@TestConfiguration
public class TestConfig {
    /** Mutable clock so tests can jump past promise and hold windows. */
    public static class TestClock extends Clock {
        private Instant now = Instant.parse("2019-11-29T14:00:00Z");
        public void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    public static class RecordingNotifier implements PickupNotifier {
        public final List<String> changes = new ArrayList<>();
        public final List<String> messages = new ArrayList<>();
        @Override public void orderChanged(PickupOrder o) { changes.add(o.getOrderNo() + ":" + o.getStatus()); }
        @Override public void customerMessage(PickupOrder o, String m) { messages.add(o.getOrderNo() + ":" + m); }
    }

    @Bean @Primary TestClock testClock() { return new TestClock(); }
    @Bean RecordingNotifier recordingNotifier() { return new RecordingNotifier(); }
}
