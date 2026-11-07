package com.limasegura.limasegurabackend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String rootHtml() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Lima Segura - API Backend</title>
                <style>
                    body { font-family: system-ui, -apple-system, sans-serif; background: #020617; color: #f8fafc; margin: 0; padding: 40px 20px; display: flex; justify-content: center; align-items: center; min-height: 80vh; }
                    .card { background: #0f172a; border: 1px solid #1e293b; border-radius: 16px; padding: 32px; max-width: 640px; width: 100%; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); }
                    .badge { display: inline-flex; align-items: center; gap: 6px; background: #064e3b; color: #6ee7b7; padding: 4px 12px; border-radius: 9999px; font-size: 12px; font-weight: 700; margin-bottom: 16px; }
                    .pulse { width: 8px; height: 8px; border-radius: 50%; background: #10b981; box-shadow: 0 0 10px #10b981; }
                    h1 { margin: 0 0 8px 0; font-size: 24px; color: #ffffff; }
                    p { color: #94a3b8; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0; }
                    .endpoints { background: #020617; border-radius: 10px; padding: 16px; font-size: 13px; font-family: monospace; border: 1px solid #1e293b; }
                    .endpoint { margin: 8px 0; display: flex; justify-content: space-between; align-items: center; }
                    .method { color: #38bdf8; font-weight: bold; }
                    a { color: #ef4444; text-decoration: none; font-weight: 600; }
                    a:hover { text-decoration: underline; }
                    .btn-group { margin-top: 24px; display: flex; gap: 12px; }
                    .btn { display: inline-block; padding: 10px 20px; border-radius: 10px; font-size: 13px; font-weight: 600; text-decoration: none; transition: all 0.2s; }
                    .btn-primary { background: #dc2626; color: white; }
                    .btn-primary:hover { background: #b91c1c; text-decoration: none; }
                    .btn-secondary { background: #1e293b; color: #e2e8f0; }
                    .btn-secondary:hover { background: #334155; text-decoration: none; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="badge"><div class="pulse"></div> BACKEND EN LÍNEA</div>
                    <h1>Lima Segura — Motor de Ingesta & API</h1>
                    <p>El backend de Spring Boot 3 y PostgreSQL está operando activamente. El pipeline de sondeo automatizado consulta fuentes de YouTube y periódicos cada 5 minutos.</p>
                    
                    <div class="endpoints">
                        <div class="endpoint"><span><span class="method">GET</span> /api/v1/incidents/heatmap</span> <a href="/api/v1/incidents/heatmap" target="_blank">Probar JSON</a></div>
                        <div class="endpoint"><span><span class="method">GET</span> /api/v1/incidents</span> <a href="/api/v1/incidents" target="_blank">Ver Lista</a></div>
                        <div class="endpoint"><span><span class="method">GET</span> /api/v1/incidents/stream</span> <a href="/api/v1/incidents/stream" target="_blank">Stream SSE</a></div>
                        <div class="endpoint"><span><span class="method">GET</span> /swagger-ui.html</span> <a href="/swagger-ui.html" target="_blank">Swagger Docs</a></div>
                    </div>

                    <div class="btn-group">
                        <a href="http://localhost:5173" class="btn btn-primary" target="_blank">Abrir Frontend Web (Puerto 5173) &rarr;</a>
                        <a href="/swagger-ui.html" class="btn btn-secondary">Documentación OpenAPI</a>
                    </div>
                </div>
            </body>
            </html>
            """;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "lima-segura-backend",
            "pipeline", "ACTIVE"
        );
    }
}
