package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.model.Report;

public class ReportCreatedEvent {

    private final Report report;

    public ReportCreatedEvent(Report report) {
        this.report = report;
    }

    public Report getReport() {
        return report;
    }
}