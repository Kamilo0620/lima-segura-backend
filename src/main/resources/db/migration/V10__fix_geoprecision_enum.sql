-- V10: Reemplaza geo_precision = 'ZONE' por el valor válido del enum GeoPrecision.NEIGHBORHOOD.
UPDATE incidents
SET geo_precision = 'NEIGHBORHOOD'
WHERE geo_precision = 'ZONE';
