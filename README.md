# 🛡️ Lima Segura - Backend API & Motor de Ingesta Inteligente

Backend desarrollado en **Spring Boot 3** y **Java 17** para la plataforma **Lima Segura: Radar Delictivo y Mapa de Calor en Tiempo Real**.

El sistema procesa, geolocaliza y pondera incidentes de criminalidad en Lima Metropolitana y el Callao mediante un pipeline autónomo de ingesta continua que extrae información de más de 20 fuentes (periódicos digitales vía RSS y scraping HTML, canales de noticias de YouTube y denuncias ciudadanas), estructurando los eventos mediante Inteligencia Artificial (LLM) y geocodificación inversa.

---

## 🚀 Arquitectura y Tecnologías Clave

* **Lenguaje:** Java 17 (LTS)
* **Framework:** Spring Boot 3.5+ (Spring Web, Spring Security 6, Spring Data JPA, Spring Validation)
* **Base de Datos:** PostgreSQL 16 con migraciones automatizadas vía **Flyway**
* **Seguridad:** Autenticación Stateless basada en tokens **JWT (HMAC-SHA256)** con control de roles (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_SERVICE`)
* **Pipeline de Ingesta:**
  * **Scheduler Autónomo:** Sondeo periódico cada 5 minutos (`@Scheduled`)
  * **Conectores:** RSS (Rome / Jsoup), Scraping HTML y metadatos de canales de YouTube
  * **Extracción de Hechos con IA:** Modelos de Lenguaje (OpenAI / Gemini compatible) con motor de respaldo heurístico NLP
  * **Resolución Geoespacial:** Coordenadas lat/lng, cálculo de distancias (Fórmula de Haversine) y vinculación con catálogo de distritos de Lima y Callao
* **Eventos en Tiempo Real:** **Server-Sent Events (SSE)** en `/api/v1/incidents/stream`

---

## 📋 Catálogo de Fuentes de Ingesta

El motor sondea periódicamente medios clasificados según su credibilidad institucional:
1. **Prensa Escrita y Portales Digitales:** El Comercio, RPP Noticias, Trome, Panamericana Noticias, La República, entre otros.
2. **Canales de Transmisión de Noticias de YouTube:** Procesamiento de títulos y coberturas informativas en tiempo real.
3. **Denuncias Ciudadanas:** Canal directo vía API pública (`POST /api/v1/incidents/report`) para reportes comunitarios con captura de coordenadas GPS.

---

## 🗄️ Esquema de Base de Datos y Migraciones Flyway

Las migraciones se ejecutan automáticamente al arrancar la aplicación:
* `V1__initial_schema.sql`: Creación de tablas (`users`, `districts`, `news_sources`, `source_items`, `incidents`, `incident_sources`, `ingestion_runs`, `zone_subscriptions`).
* `V2__sources_credibility_and_heatmap_matrix.sql`: Catálogo de 20+ fuentes periodísticas y matrices de ponderación criminal.
* `V3__sample_incidents_seed.sql`: Semilla inicial de incidentes históricos y zonas de riesgo.
* `V4__dense_sample_dataset.sql`: Dataset denso con más de 280 incidentes geolocalizados en los 43 distritos de Lima Metropolitana y el Callao.

---

## 🔌 Especificación de la API REST

### 1. Autenticación y Usuarios
* `POST /api/v1/auth/register` - Registro de nuevos usuarios ciudadanos. Retorna token JWT y rol.
* `POST /api/v1/auth/login` - Inicio de sesión y expedición de token JWT.
* `GET /api/v1/users/{id}` - Obtener perfil de usuario (Autenticado).

### 2. Incidentes & Radar Delictivo (Público)
* `GET /api/v1/incidents/heatmap` - Puntos ponderados para el mapa de calor (lat, lng, severidad, factor de atenuación temporal).
* `GET /api/v1/incidents/stats/dashboard` - Métricas agregadas: riesgo localizado, distribución horaria (00:00 - 24:00), categorías y zonas más críticas.
* `GET /api/v1/incidents` - Listado paginado de incidentes con filtros (`district`, `incidentType`, `status`, `page`, `size`).
* `GET /api/v1/incidents/{id}` - Expediente de inteligencia con fuentes originales y evidencia periodística.
* `POST /api/v1/incidents/report` - Denuncia ciudadana vecinal (ingesta directa al mapa).
* `GET /api/v1/incidents/stream` - Feed SSE en tiempo real para eventos de nuevos delitos publicados.

### 3. Consola de Administración (Requiere `ROLE_ADMIN`)
* `POST /api/v1/admin/ingestion/trigger-all` - Forzar ciclo de sondeo inmediato en todas las fuentes activas.
* `GET /api/v1/admin/ingestion-runs` - Historial de corridas del pipeline y tasa de éxito/error.
* `GET /api/v1/admin/source-items` - Auditoría de noticias y videos procesados por el motor.
* `POST /api/v1/admin/source-items/{id}/reprocess` - Reprocesamiento manual de una fuente.
* `PATCH /api/v1/admin/incidents/{id}` - Moderación y verificación de expedientes delictivos.

---

## ⚙️ Ejecución Local

### Prerrequisitos
* **Java 17+**
* **PostgreSQL 16** en `localhost:5432`
  * Base de datos: `lima_segura`
  * Usuario: `lima_segura_user`
  * Contraseña: `lima_segura_pass`

### Instrucciones
1. Clonar el repositorio:
   ```bash
   git clone https://github.com/Kamilo0620/lima-segura-backend.git
   cd lima-segura-backend
   ```
2. Compilar el proyecto:
   ```bash
   ./mvnw clean compile -DskipTests
   ```
3. Ejecutar la aplicación:
   ```bash
   ./mvnw spring-boot:run
   ```
4. El backend estará disponible en `http://localhost:8080`.

---

## 👥 Equipo y Autoría
Proyecto desarrollado para el curso **Desarrollo Basado en Plataformas** (UTEC).
* **Contribuidor:** Jasyr Valdez (`jasyr.valdez@utec.edu.pe`)
