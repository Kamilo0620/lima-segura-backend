package com.limasegura.limasegurabackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public void addEmitter(SseEmitter emitter) {
        this.emitters.add(emitter);
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));
    }

    public void removeEmitter(SseEmitter emitter) {
        this.emitters.remove(emitter);
    }

    public void broadcastIncident(Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("incident_published")
                        .data(data));
            } catch (IOException e) {
                log.warn("Failed to broadcast incident to emitter, removing it", e);
                this.emitters.remove(emitter);
            }
        }
    }

    public void broadcast(Long incidentId) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("incident-published")
                        .data(incidentId));
            } catch (IOException e) {
                log.warn("Failed to broadcast to emitter, removing it", e);
                this.emitters.remove(emitter);
            }
        }
    }
}
