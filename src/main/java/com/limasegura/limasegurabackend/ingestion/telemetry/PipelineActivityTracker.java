package com.limasegura.limasegurabackend.ingestion.telemetry;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rastreador en tiempo real de búsquedas web, ciclos de ingesta RSS/YouTube,
 * verificación cruzada y eventos de publicación/descarte para telemetría administrativa.
 */
@Component
public class PipelineActivityTracker {

    private static final int MAX_LOG_ENTRIES = 120;
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final AtomicReference<String> currentOperation = new AtomicReference<>("En espera (próximo sondeo automático)");
    private final AtomicReference<LocalDateTime> lastActivityAt = new AtomicReference<>(LocalDateTime.now(LIMA_ZONE));
    private final Deque<LogEntry> recentLogs = new ArrayDeque<>(MAX_LOG_ENTRIES);

    public synchronized void record(String stage, String level, String source, String message) {
        LocalDateTime now = LocalDateTime.now(LIMA_ZONE);
        lastActivityAt.set(now);
        if (recentLogs.size() >= MAX_LOG_ENTRIES) {
            recentLogs.removeLast();
        }
        recentLogs.addFirst(new LogEntry(now, stage, level, source, message));
    }

    public OperationToken startOperation(String operationDescription, String stage, String source) {
        activeOperations.incrementAndGet();
        currentOperation.set(operationDescription);
        record(stage, "INFO", source, operationDescription);
        return new OperationToken(operationDescription);
    }

    public void finishOperation(OperationToken token) {
        int remaining = Math.max(0, activeOperations.decrementAndGet());
        if (remaining == 0) {
            currentOperation.set("En espera (próximo sondeo cada 5 min)");
        }
        lastActivityAt.set(LocalDateTime.now(LIMA_ZONE));
    }

    public boolean isSearchingInternetNow() {
        return activeOperations.get() > 0;
    }

    public int getActiveOperationsCount() {
        return activeOperations.get();
    }

    public String getCurrentOperation() {
        return currentOperation.get();
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt.get();
    }

    public synchronized List<LogEntry> getRecentLogs(int limit) {
        int cap = Math.min(limit, recentLogs.size());
        List<LogEntry> result = new ArrayList<>(cap);
        int i = 0;
        for (LogEntry entry : recentLogs) {
            if (i++ >= cap) break;
            result.add(entry);
        }
        return result;
    }

    public record OperationToken(String description) implements AutoCloseable {
        @Override
        public void close() {
            // El llamador invoca finishOperation(token) explícitamente cuando usa instancia inyectada
        }
    }

    @Data
    @AllArgsConstructor
    public static class LogEntry {
        private LocalDateTime timestamp;
        private String stage;
        private String level;
        private String source;
        private String message;
    }
}
