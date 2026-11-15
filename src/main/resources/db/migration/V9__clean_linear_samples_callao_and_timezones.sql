-- V9: Elimina muestras lineales sintéticas, corrige direcciones "ca ...",
-- descongestiona Callao (Callejón Villegas), corrige límites costeros
-- (incluyendo muro vertical -77.0865) y normaliza horas GMT de feeds RSS.

-- 1. Eliminar incidentes sintéticos en línea diagonal (V4, 8 títulos plantilla)
DELETE FROM incident_sources
WHERE incident_id IN (
    SELECT id FROM incidents
    WHERE origin = 'SAMPLE'
      AND title IN (
          'Agresion y arrebato de celular a peaton en cruce semaforico',
          'Asalto al paso a transeunte en corredor arterial',
          'Ataque armado reportado por vecinos en arteria urbana',
          'Denuncia por cobro de cupos y extorsion a comercio local',
          'Disparos reportados durante persecucion policial en avenida',
          'Hurto agravado de pertenencias en paradero de transporte',
          'Robo a mano armada interceptado por camaras de seguridad',
          'Robo de vehiculo estacionado en via principal'
      )
);

DELETE FROM incidents
WHERE origin = 'SAMPLE'
  AND title IN (
      'Agresion y arrebato de celular a peaton en cruce semaforico',
      'Asalto al paso a transeunte en corredor arterial',
      'Ataque armado reportado por vecinos en arteria urbana',
      'Denuncia por cobro de cupos y extorsion a comercio local',
      'Disparos reportados durante persecucion policial en avenida',
      'Hurto agravado de pertenencias en paradero de transporte',
      'Robo a mano armada interceptado por camaras de seguridad',
      'Robo de vehiculo estacionado en via principal'
  );

-- 2. Deduplicar muestras SAMPLE repetidas conservando el id más antiguo
WITH dup_samples AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY title, district_ubigeo, ROUND(lat::numeric, 5), ROUND(lng::numeric, 5)
               ORDER BY id
           ) AS rn
    FROM incidents
    WHERE origin = 'SAMPLE'
)
DELETE FROM incident_sources
WHERE incident_id IN (SELECT id FROM dup_samples WHERE rn > 1);

WITH dup_samples AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY title, district_ubigeo, ROUND(lat::numeric, 5), ROUND(lng::numeric, 5)
               ORDER BY id
           ) AS rn
    FROM incidents
    WHERE origin = 'SAMPLE'
)
DELETE FROM incidents
WHERE id IN (SELECT id FROM dup_samples WHERE rn > 1);

-- 3. Limpiar direcciones falsas producto de subcadenas ("ca TV...", "cerca de...", "Bocanegra", "Jirón de")
UPDATE incidents i
SET address = 'Jurisdicción de ' || COALESCE(d.name, 'Lima')
FROM districts d
WHERE i.district_ubigeo = d.ubigeo
  AND (
      i.address ILIKE 'ca TV%'
      OR i.address ILIKE 'ca de %'
      OR i.address ILIKE 'ca Negra%'
      OR i.address ILIKE 'ca alegre%'
      OR i.address ILIKE 'Jirón de'
      OR i.address ILIKE 'Jiron de'
  );

-- 4. Corregir falsos positivos de "Santa Rosa" (150139) en titulares del Callao (070101)
UPDATE incidents
SET district_ubigeo = '070101',
    address = 'Jurisdicción de Callao'
WHERE district_ubigeo = '150139'
  AND (title ILIKE '%callao%' OR summary ILIKE '%callao%');

-- 5. Corregir salto Cieneguilla -> Ventanilla por homonimia de Av. Pachacútec (#1027 u otros)
UPDATE incidents
SET lat = -12.1120 + ((((hashtext(id::text || ':lat') & 1023) / 1023.0) - 0.5) * 0.008),
    lng = -76.8170 + ((((hashtext(id::text || ':lng') & 1023) / 1023.0) - 0.5) * 0.008),
    geo_precision = 'DISTRICT'
WHERE district_ubigeo = '150109'
  AND lat > -12.00;

-- 6. Reubicar avenidas/urbanizaciones concretas del Callao mal caídas en centroide Villegas
UPDATE incidents
SET lat = -12.0368 + (sin((hashtext(id::text || ':f') & 65535) * 0.0001) * 0.0022),
    lng = -77.0985 + (cos((hashtext(id::text || ':f') & 65535) * 0.0001) * 0.0022),
    geo_precision = 'ZONE'
WHERE district_ubigeo = '070101'
  AND address ILIKE '%Faucett%';

UPDATE incidents
SET lat = -12.0225 + (sin((hashtext(id::text || ':a') & 65535) * 0.0001) * 0.0020),
    lng = -77.1025 + (cos((hashtext(id::text || ':a') & 65535) * 0.0001) * 0.0020),
    geo_precision = 'ZONE'
WHERE district_ubigeo = '070101'
  AND address ILIKE '%Aeropuerto%';

UPDATE incidents
SET lat = -12.0540 + (sin((hashtext(id::text || ':c') & 65535) * 0.0001) * 0.0018),
    lng = -77.1235 + (cos((hashtext(id::text || ':c') & 65535) * 0.0001) * 0.0018),
    geo_precision = 'ZONE'
WHERE district_ubigeo = '070101'
  AND address ILIKE '%Carrillo Albornoz%';

-- 7. Dispersión orgánica en 9 nodos urbanos terrestres del Callao para incidentes con precisión DISTRICT
--    (elimina definitivamente el cúmulo artificioso en Callejón Villegas / Óscar R. Benavides)
WITH callao_anchors(idx, base_lat, base_lng) AS (
    VALUES
        (0, -12.0582::double precision, -77.1310::double precision),
        (1, -12.0510::double precision, -77.1270::double precision),
        (2, -12.0545::double precision, -77.1192::double precision),
        (3, -12.0462::double precision, -77.1095::double precision),
        (4, -12.0368::double precision, -77.0985::double precision),
        (5, -12.0195::double precision, -77.0995::double precision),
        (6, -12.0165::double precision, -77.1270::double precision),
        (7, -11.9885::double precision, -77.1215::double precision),
        (8, -12.0425::double precision, -77.1215::double precision)
),
hashed AS (
    SELECT i.id,
           MOD(ABS(hashtext(i.id::text || '|' || COALESCE(i.title, ''))), 9) AS anchor_idx,
           0.0007 + sqrt(((ABS(hashtext(i.id::text || ':r')) & 65535) / 65535.0)) * 0.0035 AS radius,
           ((ABS(hashtext(i.id::text || ':t')) & 65535) / 65535.0) * 2.0 * pi() AS theta
    FROM incidents i
    WHERE i.district_ubigeo = '070101'
      AND (
          i.geo_precision = 'DISTRICT'
          OR (i.lat BETWEEN -12.0580 AND -12.0550 AND i.lng BETWEEN -77.1205 AND -77.1170)
      )
)
UPDATE incidents i
SET lat = ca.base_lat + h.radius * sin(h.theta),
    lng = ca.base_lng + h.radius * cos(h.theta)
FROM hashed h
JOIN callao_anchors ca ON ca.idx = h.anchor_idx
WHERE i.id = h.id;

-- 8. Corregir los puntos aplastados en el muro vertical -77.0865 (La Perla / San Miguel)
UPDATE incidents
SET lat = CASE
        WHEN district_ubigeo = '070104' THEN -12.0735 + (sin((ABS(hashtext(id::text || ':lp')) & 65535) * 0.0002) * 0.0030)
        ELSE lat + (sin((ABS(hashtext(id::text || ':lp')) & 65535) * 0.0002) * 0.0022)
    END,
    lng = CASE
        WHEN district_ubigeo = '070104' THEN -77.1085 + (cos((ABS(hashtext(id::text || ':lp')) & 65535) * 0.0002) * 0.0055)
        ELSE -77.0840 + (cos((ABS(hashtext(id::text || ':lp')) & 65535) * 0.0002) * 0.0040)
    END
WHERE ROUND(lng::numeric, 4) = -77.0865;

-- 9. Reubicar hacia tierra firme cualquier punto residual demasiado cerca de muelles/mar en Callao (lng < -77.143)
UPDATE incidents
SET lng = -77.1380 + (((ABS(hashtext(id::text || ':coast')) & 4095) / 4095.0) * 0.0085),
    lat = lat + ((((ABS(hashtext(id::text || ':clat')) & 4095) / 4095.0) - 0.5) * 0.0025)
WHERE origin <> 'SAMPLE'
  AND lat BETWEEN -12.08 AND -11.95
  AND lng < -77.1430;

-- 10. Ajustar a hora de Lima (UTC-5) los timestamps históricos de Google News RSS guardados en GMT (07:00 / 08:00 AM)
UPDATE incidents
SET occurred_at = occurred_at - INTERVAL '5 hours'
WHERE origin = 'NEWSPAPER'
  AND occurred_at_precision IN ('APPROXIMATE', 'UNKNOWN')
  AND EXTRACT(HOUR FROM occurred_at) IN (7, 8);
