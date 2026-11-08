-- V6: los incidentes de V3 y V4 son datos de ejemplo escritos a mano para la demo.
-- Se etiquetan como SAMPLE para no presentarlos como noticias reales procesadas por un LLM.

UPDATE incidents
SET origin = 'SAMPLE',
    llm_model = NULL
WHERE llm_model IN ('llama-3.1-70b-versatile', 'pnp-dataset-import')
  AND NOT EXISTS (SELECT 1 FROM incident_sources s WHERE s.incident_id = incidents.id);
