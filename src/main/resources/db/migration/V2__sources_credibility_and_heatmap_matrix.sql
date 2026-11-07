-- V2__sources_credibility_and_heatmap_matrix.sql
-- Migración para añadir columna de credibilidad y la matriz completa de fuentes según Informe Técnico

-- 1. Añadir columna credibility a news_sources si no existe
ALTER TABLE news_sources ADD COLUMN IF NOT EXISTS credibility DOUBLE PRECISION NOT NULL DEFAULT 8.0;

-- 2. Actualizar o insertar todas las fuentes de información evaluadas
-- TV / YouTube
INSERT INTO news_sources (name, type, url, channel_id, active, credibility, poll_interval_minutes)
VALUES
('América Noticias / Cuarto Poder', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCs3Pn-8U_1eK6g9hXjXgUvw', 'UCs3Pn-8U_1eK6g9hXjXgUvw', true, 9.0, 5),
('Latina Noticias', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCpB5vKzE9T599vG5xGZ2eag', 'UCpB5vKzE9T599vG5xGZ2eag', true, 9.0, 5),
('Panamericana (24 Horas / BDP)', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UC2P791_gS2u4_0nU7b6Wbqw', 'UC2P791_gS2u4_0nU7b6Wbqw', true, 8.8, 5),
('Exitosa Noticias', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCq1X73p_P3e9-i2_ZJ4hF1g', 'UCq1X73p_P3e9-i2_ZJ4hF1g', true, 8.2, 5),
('Canal N', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCl0oN0VIzSbbS2BxGcBm_JA', 'UCl0oN0VIzSbbS2BxGcBm_JA', true, 9.3, 5),
('ATV Noticias', 'YOUTUBE_CHANNEL', 'https://www.youtube.com/feeds/videos.xml?channel_id=UCuMZJLpbgYMjCpGW2CB4t2A', 'UCuMZJLpbgYMjCpGW2CB4t2A', true, 8.0, 5),

-- Prensa Escrita y Digital
('RPP Noticias (Rotafono/Web)', 'NEWSPAPER_RSS', 'https://rpp.pe/feed/lima', null, true, 9.5, 5),
('El Comercio (Lima / Seguridad)', 'NEWSPAPER_RSS', 'https://elcomercio.pe/arcio/rss/category/lima/', null, true, 9.2, 10),
('La República (Sociedad / LR+)', 'NEWSPAPER_RSS', 'https://larepublica.pe/rss/sociedad', null, true, 8.7, 5),
('Perú21 (Lima / Actualidad)', 'NEWSPAPER_RSS', 'https://peru21.pe/arcio/rss/category/lima/', null, true, 8.3, 10),
('Infobae Perú (Perú / Seguridad)', 'NEWSPAPER_RSS', 'https://www.infobae.com/arc/outboundfeeds/rss/?outputType=xml', null, true, 8.5, 5),
('Trome (Policiales/Actualidad)', 'NEWSPAPER_RSS', 'https://trome.com/arcio/rss/category/actualidad/', null, true, 7.8, 5),
('Diario Ojo (Metrolima)', 'NEWSPAPER_RSS', 'https://ojo.pe/arcio/rss/category/metrolima/', null, true, 7.4, 10),
('Diario Correo (Lima)', 'NEWSPAPER_RSS', 'https://diariocorreo.pe/arcio/rss/category/edicion-lima/', null, true, 8.0, 10),
('Diario Extra (Prensa Roja)', 'NEWSPAPER_HTML', 'https://extra.com.pe/categoria/policial/', null, true, 6.8, 15),
('Prensa Chalaca (Callao)', 'NEWSPAPER_RSS', 'https://prensachalaca.com/feed/', null, true, 8.2, 5),

-- Fuentes Institucionales (Ground Truth)
('CGBVP (Bomberos del Perú - Dimapad)', 'INSTITUTIONAL_API', 'https://dimapad.bomberosperu.gob.pe/', null, true, 10.0, 5),
('PNP / Mininter (Alertas)', 'INSTITUTIONAL_API', 'https://www.policia.gob.pe/rss/noticias', null, true, 9.8, 15)
ON CONFLICT DO NOTHING;
