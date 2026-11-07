package com.limasegura.limasegurabackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SourceItemDiscoveredEvent extends ApplicationEvent {
    private final Long sourceItemId;

    public SourceItemDiscoveredEvent(Object source, Long sourceItemId) {
        super(source);
        this.sourceItemId = sourceItemId;
    }
}
