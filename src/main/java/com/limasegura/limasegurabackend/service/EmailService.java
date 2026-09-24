package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.model.Report;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Async
    public void sendReportCreatedEmail(Report report) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(report.getUser().getEmail());
            message.setSubject("LIMA SEGURA - Reporte recibido");
            message.setText("Hola " + report.getUser().getName() + ",\n\n" +
                    "Tu reporte en la zona " + report.getZone().getName() +
                    " ha sido registrado correctamente.\n\n" +
                    "Categoria: " + report.getCategory().getName() + "\n" +
                    "Descripcion: " + report.getDescription());
            mailSender.send(message);
            logger.info("Email de reporte creado enviado a {}", report.getUser().getEmail());
        } catch (Exception e) {
            logger.error("Error enviando email de reporte creado: {}", e.getMessage());
        }
    }

    @Async
    public void sendReportValidatedEmail(Report report) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(report.getUser().getEmail());
            message.setSubject("LIMA SEGURA - Reporte validado por la comunidad");
            message.setText("Hola " + report.getUser().getName() + ",\n\n" +
                    "Tu reporte en la zona " + report.getZone().getName() +
                    " ha sido validado por otros usuarios de la comunidad.\n\n" +
                    "Gracias por contribuir a la seguridad de Lima.");
            mailSender.send(message);
            logger.info("Email de reporte validado enviado a {}", report.getUser().getEmail());
        } catch (Exception e) {
            logger.error("Error enviando email de reporte validado: {}", e.getMessage());
        }
    }
}