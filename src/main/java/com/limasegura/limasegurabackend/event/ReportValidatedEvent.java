package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.model.Report;

public class ReportValidatedEvent {

    private final Report report;

    public ReportValidatedEvent(Report report) {
        this.report = report;
    }

    public Report getReport() {
        return report;
    }
}