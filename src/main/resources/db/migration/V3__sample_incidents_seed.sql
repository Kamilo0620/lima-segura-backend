-- V3__sample_incidents_seed.sql
-- Inserción de incidentes de prueba realistas para visualización inmediata en el mapa de calor

INSERT INTO incidents (
    incident_type, title, summary, occurred_at, occurred_at_precision,
    address, district_ubigeo, lat, lng, geo_precision, confidence,
    incident_count, status, origin, llm_model, victims_count, evidence_excerpt, created_at
) VALUES
-- 1. Miraflores - Robo a mano armada a cambista (reciente, alta gravedad)
('ARMED_ROBBERY', 'Asalto armado a cambista en Av. Larco', 
 'Dos delincuentes en motocicleta interceptaron a mano armada a un cambista despojándolo de 15 mil dólares en efectivo antes de fugar con dirección a la Vía Expresa.',
 NOW() - INTERVAL '2 hours', 'EXACT', 'Av. José Larco cdra. 6', '150122', -12.1225, -77.0298, 'EXACT_ADDRESS', 0.96, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 1, 'encañonaron al cambista y le arrebataron el bolso con 15 mil dólares', NOW() - INTERVAL '2 hours'),

-- 2. San Juan de Lurigancho - Sicariato y extorsión a transportistas
('HOMICIDE', 'Ataque de sicarios contra paradero de combis en Canto Grande',
 'Presuntos miembros de banda de extorsionadores abrieron fuego contra unidad de transporte público dejando a una persona fallecida y un herido por negarse al cobro de cupos.',
 NOW() - INTERVAL '4 hours', 'APPROXIMATE', 'Paradero 8 de Canto Grande', '150132', -11.9685, -76.9942, 'INTERSECTION', 0.98, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 2, 'dispararon seis veces contra el chofer tras recibir amenazas de cobro de cupos', NOW() - INTERVAL '4 hours'),

-- 3. Comas - Cobro de cupos y detonación de artefacto explosivo
('EXTORTION', 'Detonación de artefacto explosivo en botica en Av. Túpac Amaru',
 'Delincuentes detonaron una carga de dinamita en la puerta de una conocida botica en Comas, dejando cuantiosos daños materiales y una nota extorsiva exigiendo 20 mil soles.',
 NOW() - INTERVAL '10 hours', 'EXACT', 'Av. Túpac Amaru km 11', '150110', -11.9340, -77.0490, 'EXACT_ADDRESS', 0.92, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 0, 'dejaron una nota con amenazas de muerte exigiendo pago de cupo mensual', NOW() - INTERVAL '10 hours'),

-- 4. Los Olivos - Robo frustrado en banco con balacera
('ARMED_ROBBERY', 'Intento de asalto a agencia financiera en Av. Antúnez de Mayolo',
 'La Policía Nacional capturó a tres hampones armados tras intenso tiroteo cuando intentaban asaltar a clientes que salían de una agencia bancaria en Los Olivos.',
 NOW() - INTERVAL '18 hours', 'EXACT', 'Av. Carlos Izaguirre con Antúnez de Mayolo', '150117', -11.9560, -77.0665, 'INTERSECTION', 0.95, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 1, 'efectivos policiales redujeron a los asaltantes tras persecución armada', NOW() - INTERVAL '18 hours'),

-- 5. Cercado de Lima - Asalto a peatones en Jr. de la Unión
('THEFT', 'Arrebato de celulares y pertenencias en Jr. Huancavelica',
 'Falso repartidor en moto sustrajo teléfonos móviles a dos turistas que transitaban por el centro histórico de Lima antes de ser interceptado por el serenazgo.',
 NOW() - INTERVAL '28 hours', 'EXACT', 'Jr. de la Unión con Jr. Huancavelica', '150101', -12.0460, -77.0335, 'INTERSECTION', 0.88, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 2, 'el sujeto a bordo de moto lineal jaló los bolsos de los transeúntes', NOW() - INTERVAL '28 hours'),

-- 6. Callao - Operativo policial con incautación de armas y drogas
('DRUG_TRAFFICKING', 'Desarticulación de banda en Puerto Nuevo',
 'La División Policial del Callao allanó tres inmuebles incautando más de 500 envoltorios de PBC, dos armas de fuego ilegales y chalecos antibalas.',
 NOW() - INTERVAL '35 hours', 'APPROXIMATE', 'Zona de Puerto Nuevo', '070101', -12.0530, -77.1250, 'NEIGHBORHOOD', 0.94, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 0, 'se hallaron pistolas con serie limada y microcomercialización de estupefacientes', NOW() - INTERVAL '35 hours'),

-- 7. San Martín de Porres - Asalto en restaurante campestre
('ARMED_ROBBERY', 'Asalto armado a comensales en Av. Perú',
 'Cuatro delincuentes armados ingresaron a una cevichería en San Martín de Porres, despojando a clientes y dueños del dinero de la caja registradora y teléfonos celulares.',
 NOW() - INTERVAL '50 hours', 'EXACT', 'Av. Perú cdra. 22', '150135', -12.0295, -77.0720, 'EXACT_ADDRESS', 0.91, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 6, 'amenazaron a familias con armas cortas y se llevaron la venta del día', NOW() - INTERVAL '50 hours'),

-- 8. Surco - Secuestro al paso frustrado en Av. Primavera
('KIDNAPPING', 'Intento de secuestro al paso en Av. Primavera',
 'Agentes de serenazgo de Surco y patrullaje integrado frustraron el secuestro de una empresaria que fue emboscada en su vehículo por sujetos encapuchados.',
 NOW() - INTERVAL '60 hours', 'EXACT', 'Av. Primavera con Av. Encalada', '150140', -12.1110, -76.9830, 'INTERSECTION', 0.97, 1, 'PUBLISHED', 'NEWS', 'llama-3.1-70b-versatile', 1, 'intentaron obligar a la víctima a subir a una camioneta polarizada', NOW() - INTERVAL '60 hours'),

-- 9. Datos Oficiales PNP - Registro consolidado Comisaría Alfonso Ugarte
('THEFT', 'Registro oficial PNP: Reporte de hurto en vía pública',
 'Datos agregados correspondientes al parte policial de hurtos en vía pública en la jurisdicción del Cercado de Lima.',
 NOW() - INTERVAL '72 hours', 'APPROXIMATE', 'Av. Alfonso Ugarte cdra. 8', '150101', -12.0490, -77.0440, 'DISTRICT', 1.0, 15, 'PUBLISHED', 'OFFICIAL', 'pnp-dataset-import', 15, 'Dataset oficial Sistema SIDPOL PNP', NOW() - INTERVAL '72 hours')
ON CONFLICT DO NOTHING;
