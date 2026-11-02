package pl.pw.zai.pomiary.live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.pw.zai.pomiary.web.dto.Dtos.MeasurementDto;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Rozsyła nowe wyniki do przeglądarek przez Server-Sent Events
@Component
public class MeasurementBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MeasurementBroadcaster.class);
    private static final int MAX_CLIENTS = 200;
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    public record MeasurementSaved(MeasurementDto measurement) {}

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        if (emitters.size() >= MAX_CLIENTS) return null;
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().comment("connected").reconnectTime(5000));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    @TransactionalEventListener
    public void onSaved(MeasurementSaved event) {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name("measurement").data(event.measurement(), MediaType.APPLICATION_JSON));
        }
    }

    // komentarz co 20 s utrzymuje połączenie przy proxy zamykających bezczynne połączenia
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("ping"));
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            log.debug("Zamknięto połączenie SSE: {}", e.getMessage());
        }
    }
}
