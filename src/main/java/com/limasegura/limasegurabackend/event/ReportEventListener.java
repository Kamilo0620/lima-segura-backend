package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ReportEventListener.class);

    private final EmailService emailService;

    @Async
    @EventListener
    public void handleReportCreated(ReportCreatedEvent event) {
        logger.info("Procesando evento de reporte creado, id: {}", event.getReport().getId());
        emailService.sendReportCreatedEmail(event.getReport());
    }

    @Async
    @EventListener
    public void handleReportValidated(ReportValidatedEvent event) {
        logger.info("Procesando evento de reporte validado, id: {}", event.getReport().getId());
        emailService.sendReportValidatedEmail(event.getReport());
    }
}