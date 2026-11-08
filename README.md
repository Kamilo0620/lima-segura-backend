# LIMA SEGURA

## CS 2031 - Desarrollo Basado en Plataformas

**Integrantes:**
- Jasyr Arnaldo Valdez Parra
- Kamilo Alfonso Neyra Chipana
- Pablo Alejandro Zavala Tejeda
- Elías Emanuel Paz Raymondi

---

## Índice

1. [Introducción](#introducción)
2. [Identificación del Problema o Necesidad](#identificación-del-problema-o-necesidad)
3. [Descripción de la Solución](#descripción-de-la-solución)
4. [Modelo de Entidades](#modelo-de-entidades)
5. [Especificación de la API REST](#especificación-de-la-api-rest)
6. [Manejo de Errores](#manejo-de-errores)
7. [Medidas de Seguridad Implementadas](#medidas-de-seguridad-implementadas)
8. [Eventos y Asincronía](#eventos-y-asincronía)
9. [Base de Datos y Migraciones](#base-de-datos-y-migraciones)
10. [Ejecución Local](#ejecución-local)
11. [GitHub & Management](#github--management)
12. [Conclusión](#conclusión)
13. [Apéndices](#apéndices)

---

## Introducción

### Contexto

La inseguridad ciudadana es uno de los problemas más urgentes en Lima Metropolitana. La información sobre delincuencia (reportes policiales, estadísticas oficiales, noticias) se encuentra dispersa entre distintas fuentes, se actualiza con retraso y rara vez llega al ciudadano en un formato útil para tomar decisiones cotidianas, como qué ruta tomar o qué tan seguro es transitar por una zona a determinada hora.

LIMA SEGURA nace para cerrar esa brecha: centralizar información de inseguridad urbana y ponerla al alcance de cualquier persona mediante una API que alimenta un mapa de calor interactivo (web y móvil).

### Objetivos del Proyecto

- Diseñar y construir una API REST robusta que centralice incidentes de seguridad georreferenciados.
- Permitir el registro ciudadano de reportes de inseguridad, con un sistema de **validación comunitaria basado en confirmaciones**.
- Importar incidentes oficiales (PNP/INEI) mediante archivos CSV.
- Ofrecer **conteos agregados** de incidentes por franja horaria, tipo de delito y distrito.
- Implementar buenas prácticas de desarrollo backend: arquitectura en capas, manejo de excepciones, seguridad con JWT, eventos asíncronos, migraciones versionadas y control de versiones colaborativo.

---

## Identificación del Problema o Necesidad

### Descripción del Problema

No existe una plataforma que reúna y organice de forma accesible la información sobre inseguridad en Lima Metropolitana. Los datos de la PNP e INEI están dispersos, se presentan a nivel agregado (distrital) y con retraso, lo que dificulta conocer el nivel de riesgo real de una zona específica en un momento determinado.

### Justificación

Resolver este problema tiene un impacto directo en la seguridad y calidad de vida de los limeños: permite decisiones informadas sobre movilidad, complementa la información oficial con el conocimiento situado de la ciudadanía (reportes comunitarios validados por otros vecinos) y sienta las bases para políticas públicas basadas en datos.

---

## Descripción de la Solución

### Funcionalidades Implementadas

- **Autenticación y roles:** registro y login con contraseñas cifradas (BCrypt) y tokens JWT. Roles `USER`, `ADMIN` y `SERVICE` (este último solo para el worker interno). El registro público **siempre** asigna el rol `USER`.
- **Reportes ciudadanos con validación comunitaria:** un usuario autenticado reporta un incidente con ubicación (lat/lng), tipo y descripción. El reporte nace en `PENDING_REVIEW`; cuando **3 usuarios distintos** lo confirman ("yo también lo vi") pasa a `PUBLISHED` y aparece en el mapa de calor. El autor no puede confirmar su propio reporte y nadie puede confirmar dos veces.
- **Importación de datasets oficiales (CSV):** el administrador sube un archivo CSV con incidentes de la PNP/INEI (`POST /api/v1/admin/datasets/import`).
- **Estadísticas agregadas:** conteos reales de incidentes publicados por hora del día, por tipo de delito y por distrito (`GET /api/v1/incidents/stats/dashboard`).
- **Mapa de calor:** puntos ponderados por gravedad del delito, credibilidad de la fuente y antigüedad del hecho.
- **Búsqueda y paginación:** listado de incidentes paginado con filtros por tipo, distrito, estado, origen, rango de fechas y búsqueda por texto (`search`).
- **Moderación:** el administrador puede revisar, publicar o rechazar incidentes.
- **Ingesta automática de noticias (experimental, apagada por defecto):** conectores RSS, HTML y YouTube que descubren noticias y las estructuran con un LLM. Se activa con `INGESTION_ENABLED=true`. Sin LLM configurado, la extracción heurística deja los incidentes en `PENDING_REVIEW` para revisión manual.
- **Eventos en tiempo real:** feed Server-Sent Events con los incidentes recién publicados.

### Tecnologías Utilizadas

- **Lenguaje y Framework:** Java 22 (compatible con 17), Spring Boot 4.1.1 (Web, Data JPA, Security, Validation)
- **Base de datos:** PostgreSQL 16 en Docker (`docker-compose.yml`, puerto local 5433)
- **Migraciones:** Flyway (`src/main/resources/db/migration`)
- **Seguridad:** Spring Security + JWT (`jjwt`)
- **Persistencia:** Hibernate / JPA con Lombok
- **Ingesta:** Jsoup, OpenCSV, Resilience4j, ShedLock
- **Build:** Maven
- **Control de versiones:** Git y GitHub (ramas por funcionalidad y Pull Requests)

---

## Modelo de Entidades

| Entidad | Descripción |
|---|---|
| `User` | Ciudadano registrado; implementa `UserDetails` para integrarse con Spring Security. |
| `Incident` | Incidente georreferenciado. Su `origin` indica de dónde viene: `CITIZEN` (reporte ciudadano), `OFFICIAL_PNP` / `OFFICIAL_INEI` (CSV), `NEWSPAPER` / `YOUTUBE` (ingesta) o `SAMPLE` (datos de ejemplo). Estados: `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`. |
| `Confirmation` | Confirmación de un usuario sobre un reporte ciudadano. Tabla puente con restricción única (incidente, usuario). |
| `District` | Distritos de Lima y Callao (ubigeo, centroide). |
| `Category` / `Zone` | Catálogos de tipos de delito y zonas (se conservan del modelo original). |
| `Prediction` | Nivel de riesgo (`LOW`/`MEDIUM`/`HIGH`) por zona, día y hora. |
| `NewsSource` / `SourceItem` / `IngestionRun` | Fuentes de noticias, artículos descubiertos y corridas del pipeline de ingesta. |
| `ZoneSubscription` | Suscripción de un usuario a alertas de una zona. |

### Relaciones principales

- `User` → `Incident` (uno a muchos, como autor del reporte ciudadano).
- `User` ↔ `Incident` (muchos a muchos, vía `Confirmation`): un usuario confirma muchos reportes y un reporte es confirmado por muchos usuarios.
- `District` → `Incident` (uno a muchos).
- `Incident` ↔ `SourceItem` (muchos a muchos, vía `incident_sources`): un incidente puede estar respaldado por varias noticias.
- `NewsSource` → `SourceItem` → `IngestionRun` (uno a muchos).

---

## Especificación de la API REST

Base: `http://localhost:8080/api/v1`

### Autenticación
- `POST /auth/register` — registro (rol siempre `USER`). Devuelve `token`, `id`, `name`, `email`, `role`.
- `POST /auth/login` — inicio de sesión.

### Incidentes
| Método | Endpoint | Acceso | Descripción |
|---|---|---|---|
| GET | `/incidents?page&size&type&district&status&origin&from&to&search` | Público | Listado paginado con filtros |
| GET | `/incidents/{id}` | Público | Detalle (incluye `confirmationsCount` y `confirmedByMe` si hay sesión) |
| POST | `/incidents/report` | Autenticado | Reporte ciudadano (nace en `PENDING_REVIEW`) |
| POST | `/incidents/{id}/confirm` | Autenticado | Confirmar un reporte ciudadano |
| GET | `/incidents/heatmap` | Público | Puntos ponderados para el mapa de calor |
| GET | `/incidents/stats/dashboard` | Público | Conteos agregados por hora, tipo y distrito |
| GET | `/incidents/stream` | Público | Feed SSE de incidentes publicados |

### Administración (`ROLE_ADMIN`)
- `PATCH /admin/incidents/{id}` — moderar (publicar/rechazar).
- `POST /admin/datasets/import` — importar CSV oficial.
- `GET/POST/PUT/DELETE /admin/sources` — gestionar fuentes de noticias.
- `POST /admin/ingestion/trigger-all` — ejecutar un ciclo de ingesta manual.
- `GET /admin/ingestion-runs`, `GET /admin/source-items` — auditoría del pipeline.

La colección completa está en `postman_collection.json`.

---

## Manejo de Errores

Jerarquía de excepciones personalizadas que heredan de `ApiException` (cada una con su código HTTP):

- `ResourceNotFoundException` (404)
- `DuplicateResourceException` (409) — por ejemplo, confirmar dos veces el mismo reporte
- `InvalidOperationException` (400) — por ejemplo, confirmar tu propio reporte
- `UnauthorizedException` (401), `ForbiddenActionException` (403)
- `InvalidFileFormatException` (400) — CSV inválido
- Excepciones del pipeline: `SourceFetchException`, `LlmExtractionException`, `GeocodingException`, etc.

Un `GlobalExceptionHandler` (`@RestControllerAdvice`) devuelve siempre un `ErrorResponse` con `timestamp`, `status`, `error`, `message` y `path`. También maneja validaciones (`MethodArgumentNotValidException`), JSON mal formado, credenciales inválidas (`AuthenticationException` → 401) y accesos denegados (403), sin exponer trazas internas.

---

## Medidas de Seguridad Implementadas

- Contraseñas cifradas con **BCrypt**.
- Autenticación **stateless con JWT**, validado en cada petición por `JwtAuthenticationFilter`.
- Sin token, la API responde **401** con un mensaje claro (`JwtAuthenticationEntryPoint`).
- El registro público **no permite elegir rol**: siempre crea `USER`, evitando la escalada de privilegios.
- El usuario de un reporte o confirmación se obtiene **del token** (`@AuthenticationPrincipal`), nunca del cuerpo de la petición.
- Rutas de administración protegidas por rol (`hasRole("ADMIN")` / `@PreAuthorize`).
- Secretos por variables de entorno (`JWT_SECRET`, credenciales de BD, `LLM_API_KEY`).
- CORS configurable con `CORS_ALLOWED_ORIGINS`.
- Consultas JPA parametrizadas (prevención de inyección SQL) y validación de entrada con Bean Validation.

---

## Eventos y Asincronía

Se usa `ApplicationEventPublisher` con listeners asíncronos (`@Async` + `@TransactionalEventListener`):

1. **`IncidentPublishedEvent`**: se publica cuando un reporte ciudadano alcanza 3 confirmaciones y cuando la ingesta extrae un incidente. Lo escuchan:
   - `SseBroadcastListener`: envía el incidente a los clientes conectados al feed en tiempo real.
   - `HeatmapCacheEvictionListener`: invalida el caché del mapa de calor (asíncrono, después del commit).
2. **`SourceItemDiscoveredEvent`** → `ContentFetchListener`: descarga el contenido de una noticia en un pool de hilos propio (`ingestionExecutor`).
3. **`SourceItemContentReadyEvent`** → `IncidentExtractionListener`: extrae el incidente con el LLM en otro pool (`llmExecutor`).
4. **`IngestionFailedEvent`**: registra fallas del pipeline.

Los pools de hilos están configurados en `AsyncConfig`, para que las tareas lentas (descargas, LLM) no bloqueen las peticiones HTTP.

---

## Base de Datos y Migraciones

Las tablas se crean con **Flyway** al arrancar la aplicación (`spring.jpa.hibernate.ddl-auto=validate`):

| Migración | Contenido |
|---|---|
| `V1__initial_schema.sql` | Esquema inicial, usuario administrador y fuentes de noticias |
| `V2__sources_credibility_and_heatmap_matrix.sql` | Credibilidad de fuentes y fuentes adicionales |
| `V3__sample_incidents_seed.sql` / `V4__dense_sample_dataset.sql` | **Datos de ejemplo** (~90 incidentes) para la demo |
| `V5__citizen_reports_and_confirmations.sql` | Autor del reporte y tabla `incident_confirmations` |
| `V6__label_sample_data.sql` | Marca los datos de ejemplo con `origin = SAMPLE` |

> Los incidentes de V3 y V4 son **datos de ejemplo escritos a mano** para que el mapa no se vea vacío en la demo. No corresponden a hechos reales.

---

## Ejecución Local

### Prerrequisitos
- Java 17+ (probado con 22)
- Docker Desktop

### Pasos
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Kamilo0620/lima-segura-backend.git
   cd lima-segura-backend
   ```
2. Levantar PostgreSQL:
   ```bash
   docker compose up -d postgres
   ```
3. Ejecutar la aplicación (o con ▶ desde IntelliJ):
   ```bash
   ./mvnw spring-boot:run
   ```
4. La API queda en `http://localhost:8080`.

### Variables de Entorno

| Variable | Descripción | Por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL de PostgreSQL | `jdbc:postgresql://localhost:5433/lima_segura` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Credenciales de BD | `lima_segura_user` / `lima_segura_pass` |
| `JWT_SECRET` | Clave para firmar los JWT | Valor de desarrollo (cambiar en producción) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (separados por coma) | `http://localhost:5173,http://localhost:8081` |
| `INGESTION_ENABLED` | Activa la ingesta automática de noticias | `false` |
| `LLM_API_KEY` / `LLM_BASE_URL` / `LLM_MODEL` | Proveedor LLM compatible con OpenAI | Vacío |
| `WORKER_API_TOKEN` | Token del worker de transcripción | Vacío |

> **Importante:** el usuario administrador inicial (`admin@limasegura.pe`) se crea con una contraseña de desarrollo. Cámbienla antes de desplegar.

---

## GitHub & Management

El trabajo se organizó con ramas por funcionalidad y Pull Requests hacia `main`. Durante el Proyecto 2, un merge directo a `main` sobrescribió funcionalidades ya construidas (reportes con confirmaciones, correcciones de seguridad y documentación). Se recuperaron desde el historial de Git en una rama de integración y, desde entonces, **`main` está protegida**: solo acepta cambios mediante Pull Request.

---

## Conclusión

### Logros del Proyecto

Una API REST que combina reportes ciudadanos validados por la comunidad, datos oficiales importados por CSV y un pipeline experimental de noticias, con autenticación JWT por roles, manejo centralizado de errores, eventos asíncronos y migraciones versionadas.

### Aprendizajes Clave

- La disciplina de control de versiones (ramas, PRs y `main` protegida) es tan importante como el código: dos veces se perdió trabajo por cambios directos a `main`.
- Los datos de ejemplo deben identificarse como tales; presentarlos como datos reales resta credibilidad al sistema.
- Las funciones que dependen de servicios externos (LLM, YouTube) deben poder apagarse para no comprometer la estabilidad.

## Trabajo Futuro
- Modelo predictivo real de riesgo por zona, día y hora a partir de los conteos agregados.
- Activar la ingesta de noticias con un LLM configurado y mejorar la geolocalización (direcciones exactas en vez del centroide del distrito).
- Notificaciones por correo con plantillas HTML (Thymeleaf) al publicarse un reporte y para las suscripciones por zona.
- Refresh tokens para renovar la sesión sin un nuevo login.
- Mayor cobertura de pruebas automatizadas.

---

## Apéndices

### Licencia

Este proyecto se distribuye bajo la licencia MIT.

### Referencias

- Spring Boot: https://docs.spring.io/spring-boot/
- Spring Security: https://docs.spring.io/spring-security/
- Flyway: https://documentation.red-gate.com/flyway
- JJWT: https://github.com/jwtk/jjwt
