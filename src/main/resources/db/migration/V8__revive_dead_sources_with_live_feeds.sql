-- =====================================================================================
-- V8: Reactivar canales de prensa peruanos que retornaban 0 items en Arcio/HTML
-- usando feeds RSS en tiempo real de Google News PE acotados a Lima y Callao
-- =====================================================================================

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=site:peru21.pe+(Lima+OR+Callao)+(robo+OR+asalto+OR+sicariato+OR+extorsion+OR+balacera+OR+capturan)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name LIKE 'Per%21%';

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=site:ojo.pe+(Lima+OR+Callao)+(robo+OR+asalto+OR+sicariato+OR+extorsion+OR+balacera+OR+capturan)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name = 'Diario Ojo (Metrolima)';

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=site:diariocorreo.pe+(Lima+OR+Callao)+(robo+OR+asalto+OR+sicariato+OR+extorsion+OR+balacera+OR+capturan)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name = 'Diario Correo (Lima)';

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=(site:extra.com.pe+OR+site:trome.com)+(Lima+OR+Callao)+(asalto+OR+robo+OR+sicariato+OR+extorsion+OR+balacera)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name = 'Diario Extra (Prensa Roja)';

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=Callao+(robo+OR+asalto+OR+sicariato+OR+extorsion+OR+balacera+OR+banda+OR+detenidos)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name = 'Prensa Chalaca (Callao)';

UPDATE news_sources
SET type = 'NEWSPAPER_RSS',
    url = 'https://news.google.com/rss/search?q=site:americatv.com.pe+(Lima+OR+Callao)+(robo+OR+asalto+OR+sicariato+OR+extorsion+OR+balacera+OR+capturan)&hl=es-419&gl=PE&ceid=PE:es-419',
    consecutive_failures = 0,
    last_error = NULL
WHERE name LIKE 'Am%rica Noticias%';
