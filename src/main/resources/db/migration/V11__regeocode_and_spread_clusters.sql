-- =============================================================================
-- V11: Limpieza de direcciones truncadas ("ca. Seg", ". Los atacantes"),
--      corrección de centroides mal etiquetados como ADDRESS (Callao/Lima/SMP),
--      distribución realista a lo largo de corredores viales y dispersión
--      orgánica multi-nodo en tierra firme para evitar cúmulos en el mapa.
-- =============================================================================

-- 1. Corregir falsas direcciones truncadas del caso lingotes de oro en Av. El Derby (Surco 150140)
UPDATE incidents
SET district_ubigeo = '150140',
    address = 'Av. El Derby',
    geo_precision = 'ADDRESS',
    lat = -12.0998 + (((id % 7) - 3) * 0.00028),
    lng = -76.9688 + (((id % 5) - 2) * 0.00032)
WHERE (LOWER(COALESCE(address, '')) IN ('ca. seg', 'ca seg')
   OR LOWER(COALESCE(address, '')) LIKE '%el derby%')
  AND origin IN ('NEWSPAPER', 'YOUTUBE');

-- 2. Recortar colas de oraciones pegadas después de un punto final (p. ej. ". Los atacantes")
UPDATE incidents
SET address = BTRIM(REGEXP_REPLACE(address, '(?i)(?<!\b(?:av|jr|ca|psj|urb|cdra|km|nro|sta|sto|san|dr))\.\s+.*$', ''))
WHERE address ~ '\.\s+[A-ZÁÉÍÓÚÑ]'
  AND origin IN ('NEWSPAPER', 'YOUTUBE');

-- 3. Corregir ubicaciones puntuales desplazadas por corredores o geocoders genéricos
UPDATE incidents
SET lat = -12.0462 + (((id % 5) - 2) * 0.00022),
    lng = -77.1095 + (((id % 3) - 1) * 0.00022),
    geo_precision = 'ADDRESS'
WHERE LOWER(COALESCE(address, '')) LIKE '%minka%'
  AND district_ubigeo = '070101';

UPDATE incidents
SET lat = -12.0542 + (((id % 5) - 2) * 0.00035),
    lng = -77.1185 + (((id % 5) - 2) * 0.00045),
    geo_precision = 'ADDRESS'
WHERE (LOWER(COALESCE(address, '')) LIKE '%benavides%' OR LOWER(COALESCE(address, '')) LIKE '%colonial%')
  AND district_ubigeo = '070101';

UPDATE incidents
SET lat = -12.0556,
    lng = -77.1012,
    geo_precision = 'ADDRESS'
WHERE LOWER(COALESCE(address, '')) LIKE '%colonial%'
  AND district_ubigeo = '070102';

-- 4. Repartir incidentes en corredores arteriales (Av. Grau, Av. Argentina, Av. Gambetta) a lo largo de su eje real
UPDATE incidents
SET geo_precision = 'ADDRESS',
    lat = -12.0588 + (((id * 37) % 100) / 100.0) * 0.0032,
    lng = -77.0335 + (((id * 37) % 100) / 100.0) * 0.0135
WHERE LOWER(COALESCE(address, '')) ~ '\b(?:av\.?|avenida)\s+(?:miguel\s+)?grau\b'
  AND district_ubigeo = '150101'
  AND origin IN ('NEWSPAPER', 'YOUTUBE');

UPDATE incidents
SET geo_precision = 'ADDRESS',
    lat = -12.0436 + (((id * 53) % 100) / 100.0) * 0.0024,
    lng = -77.0495 - (((id * 53) % 100) / 100.0) * 0.0220
WHERE LOWER(COALESCE(address, '')) ~ '\b(?:av\.?|avenida)\s+argentina\b'
  AND district_ubigeo = '150101'
  AND origin IN ('NEWSPAPER', 'YOUTUBE');

UPDATE incidents
SET geo_precision = 'ADDRESS',
    lat = -11.9585 + (((id * 41) % 100) / 100.0) * 0.0520,
    lng = -77.1305 + (((id * 41) % 100) / 100.0) * 0.0040
WHERE LOWER(COALESCE(address, '')) ~ '\b(?:av\.?|avenida)\s+(?:nestor|n[eé]stor)?\s*gambetta\b'
  AND district_ubigeo = '070101'
  AND origin IN ('NEWSPAPER', 'YOUTUBE');

-- 5. Degradar falsos ADDRESS cuyo texto es solo el nombre del distrito/provincia o jurisdicción
UPDATE incidents
SET geo_precision = 'NEIGHBORHOOD'
WHERE origin IN ('NEWSPAPER', 'YOUTUBE')
  AND geo_precision = 'ADDRESS'
  AND LOWER(COALESCE(address, '')) ~ '^(sector urbano|jurisdicci[oó]n de)'
  AND ABS(lat) > 11.5;

UPDATE incidents
SET geo_precision = 'DISTRICT'
WHERE origin IN ('NEWSPAPER', 'YOUTUBE')
  AND geo_precision != 'DISTRICT'
  AND LOWER(BTRIM(COALESCE(address, ''))) ~ '^(callao|provincia constitucional del callao|callao,\s*provincia constitucional del callao|cercado de lima(,\s*lima)?|lima|san mart[ií]n de porres|ventanilla|comas|chorrillos,\s*lima|san juan de lurigancho|jurisdicci[oó]n de .*)$';

-- 6. Dispersar multi-nodo todo incidente con geo_precision = 'DISTRICT' apilado en el centroide de Callao (-12.030, -77.094)
--    o en centroides urbanos de Cercado (150101), SMP (150135), Carabayllo (150106), Rímac (150128) y Ventanilla (070107)
WITH targets AS (
    SELECT
        i.id,
        i.district_ubigeo,
        d.centroid_lat,
        d.centroid_lng,
        ROW_NUMBER() OVER (PARTITION BY i.district_ubigeo ORDER BY i.id) AS seq,
        -- Hash pseudoaleatorio determinístico en [0..1)
        ABS(((i.id * 1103515245 + 12345) % 10000)) / 10000.0 AS u1,
        ABS(((i.id * 214013 + 2531011) % 10000)) / 10000.0 AS u2
    FROM incidents i
    JOIN districts d ON d.ubigeo = i.district_ubigeo
    WHERE i.origin IN ('NEWSPAPER', 'YOUTUBE')
      AND (
          -- Pileup famoso de Callao (-12.03006, -77.09449)
          (ROUND(i.lat::numeric, 3) = -12.030 AND ROUND(i.lng::numeric, 3) = -77.094 AND i.geo_precision = 'DISTRICT')
          -- Cualquier fila DISTRICT dentro de 0.0042 del centroide bruto del distrito o parte de un cúmulo >= 3
          OR (
              i.geo_precision = 'DISTRICT'
              AND (
                  SQRT(POWER(i.lat - d.centroid_lat, 2) + POWER(i.lng - d.centroid_lng, 2)) < 0.0045
                  OR i.district_ubigeo IN ('150101', '070101', '150135', '150106', '150128', '070107')
              )
          )
      )
),
anchored AS (
    SELECT
        t.id,
        t.district_ubigeo,
        t.u1,
        t.u2,
        CASE t.district_ubigeo
            WHEN '150101' THEN (ARRAY[-12.0465, -12.0555, -12.0445, -12.0515, -12.0615, -12.0435, -12.0535, -12.0585, -12.0415, -12.0565])[((t.seq - 1) % 10) + 1]
            WHEN '070101' THEN (ARRAY[-12.0582, -12.0510, -12.0545, -12.0462, -12.0368, -12.0195, -12.0165, -11.9885, -12.0425, -12.0315, -12.0255, -11.9595])[((t.seq - 1) % 12) + 1]
            WHEN '150135' THEN (ARRAY[-12.0285, -12.0135, -11.9995, -11.9855, -12.0085, -11.9915])[((t.seq - 1) % 6) + 1]
            WHEN '150106' THEN (ARRAY[-11.8725, -11.8535, -11.8845, -11.8615, -11.8415])[((t.seq - 1) % 5) + 1]
            WHEN '150128' THEN (ARRAY[-12.0295, -12.0165, -12.0385, -12.0245])[((t.seq - 1) % 4) + 1]
            WHEN '070107' THEN (ARRAY[-11.8768, -11.8565, -11.8945, -11.8680, -11.8445, -11.8835])[((t.seq - 1) % 6) + 1]
            ELSE t.centroid_lat
        END AS base_lat,
        CASE t.district_ubigeo
            WHEN '150101' THEN (ARRAY[-77.0305, -77.0365, -77.0515, -77.0155, -77.0415, -77.0685, -77.0615, -77.0235, -77.0415, -77.0485])[((t.seq - 1) % 10) + 1]
            WHEN '070101' THEN (ARRAY[-77.1310, -77.1270, -77.1192, -77.1095, -77.0985, -77.0995, -77.1270, -77.1215, -77.1215, -77.0865, -77.1155, -77.1295])[((t.seq - 1) % 12) + 1]
            WHEN '150135' THEN (ARRAY[-77.0585, -77.0715, -77.0845, -77.0895, -77.0945, -77.0655])[((t.seq - 1) % 6) + 1]
            WHEN '150106' THEN (ARRAY[-77.0325, -77.0225, -77.0435, -77.0485, -77.0185])[((t.seq - 1) % 5) + 1]
            WHEN '150128' THEN (ARRAY[-77.0335, -77.0275, -77.0265, -77.0395])[((t.seq - 1) % 4) + 1]
            WHEN '070107' THEN (ARRAY[-77.1260, -77.1335, -77.1255, -77.1185, -77.1375, -77.1355])[((t.seq - 1) % 6) + 1]
            ELSE t.centroid_lng
        END AS base_lng,
        (0.0012 + SQRT(t.u1) * 0.0056) AS rad,
        (t.seq * 2.3999632 + t.u2 * 0.8) AS angle_rad
    FROM targets t
),
displaced AS (
    SELECT
        id,
        base_lat + rad * SIN(angle_rad) AS new_lat,
        GREATEST(
            base_lng + rad * COS(angle_rad),
            CASE
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.020 THEN -77.133
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.055 THEN -77.143
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.078 THEN -77.111
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.098 THEN -77.073
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.122 THEN -77.038
                WHEN (base_lat + rad * SIN(angle_rad)) > -12.165 THEN -77.022
                ELSE -77.016
            END + 0.0012
        ) AS safe_lng
    FROM anchored
)
UPDATE incidents i
SET lat = d.new_lat,
    lng = d.safe_lng
FROM displaced d
WHERE i.id = d.id;
