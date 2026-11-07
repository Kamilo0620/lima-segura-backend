package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseBroadcastListener {

    private final SseService sseService;

    @EventListener
    public void onIncidentPublished(IncidentPublishedEvent event) {
        log.info("Broadcasting new incident {} via SSE", event.getIncidentId());
        sseService.broadcast(event.getIncidentId());
    }
}
