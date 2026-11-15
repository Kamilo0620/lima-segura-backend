-- V7__pnp_live_rss_and_cross_verification.sql
-- Actualiza el feed institucional roto de PNP hacia consulta Google News PE filtrada para PNP/Mininter en Lima y Callao
-- e incorpora un descriptor pasivo para referencias de verificación cruzada multi-medio.

UPDATE news_sources
SET url = 'https://news.google.com/rss/search?q=Policia+Nacional+PNP+OR+Mininter+(robo+OR+sicariato+OR+extorsion+OR+banda+OR+balacera)+Lima+Callao&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name = 'PNP / Mininter (Alertas)'
  AND url LIKE '%policia.gob.pe%';

INSERT INTO news_sources (name, type, url, channel_id, active, credibility, poll_interval_minutes)
SELECT 'Verificación Cruzada Web (Medios PE)',
       'NEWSPAPER_RSS',
       'https://news.google.com/rss/search?hl=es-419&gl=PE&ceid=PE:es-419',
       null,
       false,
       8.2,
       60
WHERE NOT EXISTS (
    SELECT 1 FROM news_sources WHERE name = 'Verificación Cruzada Web (Medios PE)'
);
