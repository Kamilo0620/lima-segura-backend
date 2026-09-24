package com.limasegura.limasegurabackend.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ReportEventListener {

    @Async // <--- Hace que el método se ejecute en un hilo asíncrono
    @EventListener // <--- Escucha la publicación del evento
    public void handleReportCreated(ReportCreatedEvent event) {
        System.out.println("-> [ASYNC] Evento capturado en hilo: " + Thread.currentThread().getName());
        System.out.println("-> [ASYNC] Procesando actualización para reporte ID: " + event.getReport().getId());

        try {
            // Simulamos una tarea pesada (ej. recalcular riesgo de la zona o notificar)
            Thread.sleep(3000);
            System.out.println("-> [ASYNC] Tarea en segundo plano completada con éxito.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}