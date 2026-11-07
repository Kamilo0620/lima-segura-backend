package com.limasegura.limasegurabackend.ingestion.llm;

import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

    public String buildExtractionPrompt() {
        return """
                Eres un extractor de información de noticias policiales del Perú.
                Recibirás el texto de un artículo o la transcripción de un video de noticias,
                más la fecha de publicación (published_at).

                Reglas estrictas:
                1. Extrae SOLO hechos delictivos o de seguridad ciudadana ocurridos en Perú.
                   Ignora opinión, política, deportes, farándula y noticias internacionales.
                2. NO inventes datos. Si algo no aparece explícitamente, usa null.
                3. Resuelve fechas relativas ("ayer", "anoche", "el lunes") usando published_at.
                4. No incluyas nombres de víctimas ni de menores de edad.
                5. Un texto puede contener varios incidentes distintos; devuelve uno por hecho.
                6. Si no hay ningún incidente relevante, devuelve {"incidents": []}.
                7. Responde ÚNICAMENTE con JSON válido, sin texto adicional.

                Esquema de salida:
                {
                  "incidents": [
                    {
                      "incident_type": "ROBBERY | THEFT | EXTORTION | HOMICIDE | KIDNAPPING | ASSAULT | FRAUD | DRUG_TRAFFICKING | SEXUAL_ASSAULT | VANDALISM | SHOOTING | HIT_AND_RUN | FEMICIDE | OTHER",
                      "title": "string (máx. 120 caracteres)",
                      "summary": "string (2-3 oraciones)",
                      "occurred_at": "2026-09-23T21:30:00-05:00 | null",
                      "occurred_at_precision": "EXACT | DATE_ONLY | UNKNOWN",
                      "location": {
                        "address_text": "string | null",
                        "landmark": "string | null",
                        "district": "string | null",
                        "province": "string | null",
                        "department": "string | null"
                      },
                      "victims_count": 0,
                      "confidence": 0.0,
                      "evidence_excerpt": "fragmento literal de ≤ 200 caracteres que respalda el hecho"
                    }
                  ]
                }
                """;
    }

    public String buildUserMessage(String contentText, String publishedAt) {
        // Truncate content to avoid exceeding token limits
        String truncated = contentText.length() > 15000
                ? contentText.substring(0, 15000) + "\n\n[TEXTO TRUNCADO]"
                : contentText;

        return "published_at: " + publishedAt + "\n\n---\n\n" + truncated;
    }

    public String buildDeduplicationPrompt(String title1, String summary1, String title2, String summary2) {
        return """
                Eres un asistente que compara dos descripciones de incidentes.
                Determina si ambas describen el MISMO hecho delictivo.
                
                Incidente 1:
                Título: %s
                Resumen: %s
                
                Incidente 2:
                Título: %s
                Resumen: %s
                
                Responde ÚNICAMENTE con JSON: {"same_event": true} o {"same_event": false}
                """.formatted(title1, summary1, title2, summary2);
    }
}
