package com.limasegura.limasegurabackend.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class IngestionFailedEvent extends ApplicationEvent {
    private final Long newsSourceId;
    private final String errorMessage;

    public IngestionFailedEvent(Object source, Long newsSourceId, String errorMessage) {
        super(source);
        this.newsSourceId = newsSourceId;
        this.errorMessage = errorMessage;
    }
}
