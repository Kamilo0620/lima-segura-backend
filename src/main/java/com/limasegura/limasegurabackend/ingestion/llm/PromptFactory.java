package com.limasegura.limasegurabackend.ingestion.llm;

import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

    public String buildExtractionPrompt() {
        return """
                Eres un sistema experto de inteligencia criminal y geolocalización para "Lima Segura" (exclusivamente Lima Metropolitana y Provincia Constitucional del Callao, Perú).
                Recibirás TANTO el TITULAR (title) como el CONTENIDO (content) de una noticia de prensa o de un video policial de YouTube,
                junto con la fecha de publicación (published_at).

                Reglas estrictas e innegociables de análisis y filtrado:
                1. SOLO DELITOS CALLEJEROS Y DE RIESGO CIUDADANO EN LIMA Y CALLAO:
                   - Únicamente debes extraer delitos que pongan en riesgo físico o patrimonial directo a los ciudadanos en las calles, transporte o negocios de Lima Metropolitana o el Callao:
                     robos, asaltos a mano armada, hurtos/arrebatos, sicariato, homicidios, asesinatos, balaceras/tiroteos, feminicidios, extorsiones/cobro de cupos/detonación de explosivos, secuestros, agresiones violentas en vía pública y captura de bandas armadas.
                2. RECHAZO ABSOLUTO (devuelve {"incidents": []}):
                   - Cualquier noticia de OTRO PAÍS (EE. UU., Nueva Jersey, China, Polonia, Cracovia, Colombia, Argentina, México, Venezuela, España, etc.).
                   - Cualquier noticia de OTRA PROVINCIA del Perú fuera de Lima Metropolitana y Callao (Jaén, Huaura, Huacho, Trujillo, Arequipa, Piura, Cusco, Ica, etc.).
                   - Casos de lavado de activos, incautación de mansiones o bienes de personajes públicos/farándula (Pronabi, Karla Casós, delitos financieros o de cuello blanco que no ocurren como asalto en la calle).
                   - Sentencias judiciales o condenas por hechos pasados, extradiciones, accidentes de tránsito, incendios fortuitos, rescates en el mar, notas de opinión o anuncios institucionales.
                3. GEOLOCALIZACIÓN DE MÁXIMA PRECISIÓN EN TIERRA FIRME:
                   - Extrae en "address_text" la vía, avenida, jirón, calle, cuadra, cruce, puente, estación, mercado o urbanización más exacta mencionada en el título o contenido.
                   - Extrae en "district" el nombre oficial del distrito de Lima Metropolitana o del Callao. Si el texto no menciona ningún lugar de Lima o Callao, devuelve {"incidents": []}.
                   - Calcula en "approx_lat" y "approx_lng" las coordenadas GPS reales y precisas sobre TIERRA FIRME de la avenida o lugar exacto. Si el hecho ocurrió en la Costa Verde, San Miguel, Magdalena, Miraflores, Barranco, Chorrillos o Callao, coloca la coordenada exactamente sobre la vía terrestre o malecón, NUNCA sobre el mar.
                4. Resuelve la fecha y hora exacta en que ocurrió el delito (occurred_at) usando published_at.
                5. Responde ÚNICAMENTE con JSON válido, sin bloques markdown ni texto adicional.

                Esquema de salida:
                {
                  "incidents": [
                    {
                      "incident_type": "ROBBERY | ARMED_ROBBERY | THEFT | VEHICLE_THEFT | EXTORTION | HOMICIDE | KIDNAPPING | ASSAULT | SHOOTING | FEMICIDE | VANDALISM",
                      "title": "string conciso y periodístico (máx. 120 caracteres)",
                      "summary": "string muy detallado de 4 a 5 oraciones completas explicando las circunstancias del delito, la modalidad empleada, el lugar exacto de los hechos en Lima o Callao, la intervención policial o de serenazgo y el estado de las víctimas o detenidos",
                      "occurred_at": "2026-11-09T13:30:00-05:00",
                      "occurred_at_precision": "EXACT | DATE_ONLY | UNKNOWN",
                      "location": {
                        "address_text": "string con avenida/calle/cuadra/cruce/urbanización exacta",
                        "landmark": "string con punto de referencia exacto",
                        "district": "string con el distrito de Lima o Callao",
                        "province": "Lima | Callao",
                        "department": "Lima | Callao",
                        "approx_lat": -12.0560,
                        "approx_lng": -77.0280
                      },
                      "victims_count": 1,
                      "confidence": 0.92,
                      "evidence_excerpt": "fragmento literal de ≤ 200 caracteres que respalda el delito y la ubicación"
                    }
                  ]
                }
                """;
    }

    public String buildUserMessage(String title, String contentText, String publishedAt) {
        String safeTitle = title != null ? title.trim() : "Sin título";
        String safeContent = contentText != null ? contentText.trim() : "";
        String truncated = safeContent.length() > 14000
                ? safeContent.substring(0, 14000) + "\n\n[TEXTO TRUNCADO]"
                : safeContent;

        return "published_at: " + publishedAt
                + "\nTITULAR DE LA NOTICIA / VIDEO: " + safeTitle
                + "\n\n--- CONTENIDO COMPLETO ---\n" + truncated;
    }

    public String buildUserMessage(String contentText, String publishedAt) {
        return buildUserMessage("", contentText, publishedAt);
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
