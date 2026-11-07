package com.limasegura.limasegurabackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class IncidentPublishedEvent extends ApplicationEvent {
    private final Long incidentId;

    public IncidentPublishedEvent(Object source, Long incidentId) {
        super(source);
        this.incidentId = incidentId;
    }
}
