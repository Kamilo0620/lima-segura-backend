package com.limasegura.limasegurabackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class SourceItemContentReadyEvent extends ApplicationEvent {
    private final Long sourceItemId;

    public SourceItemContentReadyEvent(Object source, Long sourceItemId) {
        super(source);
        this.sourceItemId = sourceItemId;
    }
}
