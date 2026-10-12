package com.example.funfridaygame;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A minimal browser stand-in: one STOMP connection with the three private queues subscribed. */
final class StompTestClient implements AutoCloseable {

    private static final long TIMEOUT_MS = 5_000;

    private final StompSession session;
    private final LinkedBlockingQueue<Map<String, Object>> replies = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Map<String, Object>> snapshots = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Map<String, Object>> events = new LinkedBlockingQueue<>();
    private final List<Map<String, Object>> stream = new ArrayList<>();
    private Map<String, Object> latest;

    private StompTestClient(StompSession session) {
        this.session = session;
        subscribe("/user/queue/reply", replies);
        subscribe("/user/queue/room", snapshots);
        subscribe("/user/queue/events", events);
    }

    static StompTestClient connect(int port) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session =
                client.connectAsync(
                                "ws://localhost:" + port + "/ws/websocket",
                                new StompSessionHandlerAdapter() {})
                        .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        StompTestClient testClient = new StompTestClient(session);
        Thread.sleep(100); // let the SUBSCRIBE frames reach the broker
        return testClient;
    }

    private void subscribe(String destination, LinkedBlockingQueue<Map<String, Object>> target) {
        session.subscribe(
                destination,
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public void handleFrame(StompHeaders headers, Object payload) {
                        target.add((Map<String, Object>) payload);
                    }
                });
    }

    void subscribeStream(String code) {
        LinkedBlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        subscribe("/topic/rooms/" + code + "/stream", queue);
        Thread.ofVirtual()
                .start(
                        () -> {
                            while (true) {
                                try {
                                    Map<String, Object> message = queue.take();
                                    synchronized (stream) {
                                        stream.add(message);
                                    }
                                } catch (InterruptedException e) {
                                    return;
                                }
                            }
                        });
    }

    /** Sends a request and waits for its reply. */
    Map<String, Object> request(String destination, Map<String, Object> body) throws Exception {
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> payload = new java.util.LinkedHashMap<>(body);
        payload.put("requestId", requestId);
        session.send("/app/rooms/" + destination, payload);
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> reply = replies.poll(100, TimeUnit.MILLISECONDS);
            if (reply != null && requestId.equals(reply.get("requestId"))) {
                return reply;
            }
        }
        throw new AssertionError("No reply to " + destination);
    }

    /** Waits for a snapshot matching {@code condition} (or returns the latest if it matches). */
    Map<String, Object> awaitSnapshot(Predicate<Map<String, Object>> condition) throws Exception {
        if (latest != null && condition.test(latest)) {
            return latest;
        }
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> snapshot = snapshots.poll(100, TimeUnit.MILLISECONDS);
            if (snapshot != null) {
                latest = snapshot;
                if (condition.test(snapshot)) {
                    return snapshot;
                }
            }
        }
        throw new AssertionError("Expected snapshot never arrived; latest: " + latest);
    }

    Map<String, Object> awaitPhase(String phase) throws Exception {
        return awaitSnapshot(s -> phase.equals(s.get("phase")));
    }

    Map<String, Object> awaitEvent(String type) throws Exception {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> event = events.poll(100, TimeUnit.MILLISECONDS);
            if (event != null && type.equals(event.get("type"))) {
                return event;
            }
        }
        throw new AssertionError("Event " + type + " never arrived");
    }

    List<Map<String, Object>> streamMessages() {
        synchronized (stream) {
            return List.copyOf(stream);
        }
    }

    void assertNoSnapshotWithin(Duration duration) throws InterruptedException {
        assertThat(snapshots.poll(duration.toMillis(), TimeUnit.MILLISECONDS)).isNull();
    }

    @Override
    public void close() {
        if (session.isConnected()) {
            session.disconnect();
        }
    }
}
