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
5. [Manejo de Errores](#manejo-de-errores)
6. [Medidas de Seguridad Implementadas](#medidas-de-seguridad-implementadas)
7. [Eventos y Asincronía](#eventos-y-asincronía)
8. [GitHub & Management](#github--management)
9. [Conclusión](#conclusión)
10. [Apéndices](#apéndices)

---

## Introducción

### Contexto

La inseguridad ciudadana es uno de los problemas más urgentes en Lima Metropolitana. La información sobre delincuencia (reportes policiales, estadísticas oficiales) se encuentra dispersa entre distintas fuentes gubernamentales, se actualiza con retraso, y rara vez llega al ciudadano de a pie en un formato útil para tomar decisiones cotidianas, como qué ruta tomar o qué tan seguro es transitar por una zona a determinada hora.

LIMA SEGURA nace para cerrar esa brecha: centralizar información de inseguridad urbana y ponerla al alcance de cualquier persona mediante una API que sirve de base para un mapa de calor interactivo.

### Objetivos del Proyecto

- Diseñar y construir una API REST robusta que centralice datos de incidentes de seguridad por zona, categoría y fecha.
- Permitir el registro ciudadano de reportes de inseguridad, con un sistema de validación comunitaria basado en confirmaciones.
- Sentar las bases de un sistema de predicción de riesgo por zona, día y hora.
- Implementar buenas prácticas de desarrollo backend: arquitectura en capas, manejo de excepciones, seguridad con JWT, eventos asíncronos y control de versiones colaborativo.

---

## Identificación del Problema o Necesidad

### Descripción del Problema

Actualmente no existe una plataforma que reúna y organice de forma accesible la información sobre inseguridad en Lima Metropolitana. Los datos de la PNP e INEI están dispersos, se presentan a nivel agregado (distrital) y con retraso, dificultando conocer el nivel de riesgo real de una zona específica en un momento determinado.

### Justificación

Resolver este problema tiene un impacto directo en la seguridad y calidad de vida de los limeños: permite decisiones informadas sobre movilidad, complementa la información oficial con el conocimiento situado de la ciudadanía (reportes comunitarios), y sienta las bases para futuras políticas públicas basadas en datos.

---

## Descripción de la Solución

### Funcionalidades Implementadas

- **Gestión de usuarios y autenticación:** registro y login con contraseñas cifradas (BCrypt) y emisión de tokens JWT.
- **Autorización por roles:** endpoints sensibles protegidos con `@PreAuthorize`, distinguiendo entre usuarios `USER` y `ADMIN`.
- **Gestión de Zonas, Categorías e Incidentes oficiales:** CRUD completo para administrar las unidades geográficas de análisis, los tipos de delito, y los incidentes importados de fuentes oficiales.
- **Reportes ciudadanos:** cualquier usuario autenticado puede reportar un incidente en una zona, con ubicación exacta (latitud/longitud), categoría y descripción.
- **Validación comunitaria de reportes:** un reporte pasa de `PENDING` a `VALIDATED` automáticamente cuando junta un mínimo de confirmaciones de otros usuarios, evitando duplicados por usuario.
- **Predicciones de riesgo:** estructura para almacenar el nivel de riesgo calculado (`LOW`/`MEDIUM`/`HIGH`) por zona, día de la semana y hora.
- **Notificaciones por correo:** al crear un reporte y al validarse por la comunidad, el usuario recibe un correo automático (procesado de forma asíncrona).
- **Manejo centralizado de errores:** respuestas de error consistentes y predecibles en toda la API.
- **Capa de DTOs completa:** 20 DTOs especializados (Create/Update/Response/DetailResponse) para los 7 recursos, con mapeo automático vía ModelMapper, garantizando que información sensible (como contraseñas hasheadas) nunca se exponga en las respuestas de la API.

### Tecnologías Utilizadas

- **Lenguaje y Framework:** Java 22, Spring Boot 4.1.1 (Spring Web, Spring Data JPA, Spring Security, Spring Validation, Spring Mail)
- **Base de datos:** PostgreSQL 16, ejecutada en un contenedor Docker (`docker-compose.yml`)
- **Seguridad:** Spring Security + JWT (librería `jjwt`)
- **Persistencia:** Hibernate / JPA con Lombok para reducir código repetitivo
- **Build:** Maven
- **Control de versiones:** Git y GitHub, con Issues y Projects para gestión de tareas
- **Mapeo de DTOs:** ModelMapper, para convertir automáticamente entre entidades JPA y DTOs de entrada/salida

---

## Modelo de Entidades

El sistema se apoya en **7 entidades principales**:

| Entidad | Descripción |
|---|---|
| `User` | Ciudadano registrado en la plataforma; implementa `UserDetails` para integrarse con Spring Security. |
| `Category` | Tipo de delito (robo, hurto, agresión, etc.). |
| `Zone` | Unidad geográfica de análisis (cuadra o punto específico), con coordenadas. |
| `Report` | Incidente reportado por un usuario, con estado de validación (`PENDING`, `VALIDATED`, `UNVERIFIED`). |
| `Incident` | Registro oficial de un incidente, importado de fuentes como PNP/INEI. |
| `Confirmation` | Validación de un `Report` por parte de otro usuario; tabla puente que evita duplicados. |
| `Prediction` | Nivel de riesgo estimado para una zona, día de la semana y hora. |

### Relaciones principales

- `User` → `Report` (uno a muchos): un usuario crea muchos reportes.
- `Zone` → `Report`, `Zone` → `Incident`, `Zone` → `Prediction` (uno a muchos).
- `Category` → `Report`, `Category` → `Incident` (uno a muchos).
- `User` ↔ `Report` (muchos a muchos, vía `Confirmation`): un usuario confirma muchos reportes, un reporte es confirmado por muchos usuarios.
- `Zone` ↔ `Category` (muchos a muchos, derivada de `Report`/`Incident`): una zona concentra múltiples categorías de delito.

Todas las relaciones usan `fetch = FetchType.LAZY` para optimizar el rendimiento, y `cascade = CascadeType.ALL` con `orphanRemoval = true` donde tiene sentido eliminar en cascada (por ejemplo, al borrar una Zona).

---

## Manejo de Errores

La API implementa una jerarquía de **7 excepciones personalizadas**, todas heredando de una clase base abstracta `ApiException` que encapsula su propio código HTTP:

- `ResourceNotFoundException` (404)
- `DuplicateResourceException` (409)
- `InvalidOperationException` (400)
- `UnauthorizedException` (401)
- `ForbiddenActionException` (403)
- `InvalidFileFormatException` (400)

Un `GlobalExceptionHandler` (`@RestControllerAdvice`) centraliza el manejo de todas las excepciones de la aplicación, incluyendo las de validación de Spring (`MethodArgumentNotValidException`), errores de formato JSON (`HttpMessageNotReadableException`), fallos de autorización de Spring Security (`AuthorizationDeniedException`), y cualquier error inesperado, devolviendo siempre una respuesta consistente (`ErrorResponse`) con `timestamp`, `status`, `error`, `message` y `path`. Este manejo centralizado evita que el cliente reciba trazas de error internas del servidor, y permite loguear los errores reales (vía SLF4J) para depuración sin exponer detalles sensibles.

---

## Medidas de Seguridad Implementadas

### Seguridad de Datos

- Las contraseñas se almacenan cifradas con **BCrypt**, nunca en texto plano.
- La autenticación se maneja mediante **JSON Web Tokens (JWT)**, generados en el login y validados en cada petición protegida por un `JwtAuthenticationFilter`.
- La clave secreta del JWT se configura mediante variable de entorno (`JWT_SECRET`), no está hardcodeada en el código fuente.
- El acceso a endpoints sensibles está restringido por rol (`USER`/`ADMIN`) usando `@PreAuthorize`.

### Prevención de Vulnerabilidades

- El uso de **JPA/Hibernate** con consultas parametrizadas previene inyección SQL.
- Las validaciones de entrada (`@NotBlank`, `@Email`, `@Size`, etc.) se aplican en cada DTO/entidad antes de procesar una petición.
- Spring Security gestiona la configuración de CORS y las rutas públicas vs. protegidas de forma centralizada.

---

## Eventos y Asincronía

El sistema implementa un modelo de eventos desacoplado usando `ApplicationEventPublisher` de Spring, con **dos casos de uso**:

1. **`ReportCreatedEvent`**: se publica cuando un usuario crea un nuevo reporte. Un listener asíncrono (`@Async @EventListener`) envía un correo de confirmación al usuario.
2. **`ReportValidatedEvent`**: se publica cuando un reporte junta suficientes confirmaciones comunitarias y cambia su estado a `VALIDATED`. El mismo listener dispara un segundo correo notificando al usuario que su reporte fue validado.

Estos procesos se ejecutan de forma **asíncrona** (`@Async`, habilitado con `@EnableAsync`) para no bloquear el hilo principal de la petición HTTP: el usuario recibe la respuesta de su reporte de inmediato, mientras el envío del correo ocurre en segundo plano. Esto es importante porque el envío de correo depende de un servicio externo (SMTP) cuya latencia no debe impactar la experiencia del usuario ni el tiempo de respuesta de la API.

---

## GitHub & Management

El equipo organizó el trabajo restante mediante **GitHub Issues**, uno por cada gran componente pendiente (DTOs, Seguridad/JWT, Eventos/Asincronía, Documentación), asignados individualmente a cada integrante, y un **GitHub Project** en formato tablero Kanban (To Do / In Progress / Done) para visualizar el avance del equipo.

Cada integrante trabajó sobre su propia rama `feature/`, con la intención de fusionar los cambios a `main` mediante Pull Requests revisados por el líder del equipo antes de integrarse — práctica que permitió detectar y corregir a tiempo conflictos entre las partes desarrolladas en paralelo (por ejemplo, cuando la lógica de confirmaciones de reportes fue sobrescrita accidentalmente al integrar el módulo de eventos, y se restauró tras una revisión de código).

---

## Conclusión

### Logros del Proyecto

Se construyó una API REST funcional con 7 entidades correctamente modeladas y relacionadas, autenticación y autorización basada en JWT y roles, manejo centralizado y consistente de errores, y un sistema de eventos asíncronos con notificaciones reales por correo — todo validado end-to-end mediante pruebas manuales mediante Postman.

### Aprendizajes Clave

El desarrollo en equipo reveló la importancia de un flujo de control de versiones disciplinado: los cambios directos a `main` sin revisión previa generaron una pérdida real de funcionalidad ya construida, que solo se detectó gracias a pruebas exhaustivas antes de la entrega. Esto reforzó el valor de las Pull Requests y el testing manual constante durante el desarrollo, no solo al final.

## Trabajo Futuro
- **Implementar refresh tokens** en el sistema de autenticación JWT, para permitir renovar la sesión sin requerir un nuevo login.
- **Agregar un tercer caso de uso de eventos asíncronos** (por ejemplo, notificar la creación de un Incidente oficial), ampliando el sistema de eventos más allá de la creación y validación de reportes.
- **Configurar un ThreadPoolTaskExecutor personalizado** para el procesamiento asíncrono, en vez de depender del executor por defecto de Spring.
- **Incorporar plantillas HTML (Thymeleaf)** en los correos enviados por el sistema, en vez de texto plano.
- **Implementar la ingesta de datasets oficiales de PNP/INEI vía CSV/Excel**, tal como lo recomendó el equipo docente.
- **Calcular y almacenar conteos agregados de incidentes** por Zona, Categoría y rango horario/fecha.
- **Ampliar la cobertura de pruebas automatizadas** (unitarias y de integración) para las capas de Service y Controller.

## Variables de Entorno Requeridas

| Variable | Descripción | Requerida |
|---|---|---|
| `JWT_SECRET` | Clave secreta para firmar los tokens JWT | No (tiene valor por defecto) |
| `MAIL_USERNAME` | Correo Gmail usado para enviar notificaciones | No (la app arranca sin ella; el envío de correo simplemente no funcionará) |
| `MAIL_PASSWORD` | Contraseña de aplicación de Gmail | No (mismo caso que `MAIL_USERNAME`) |

Para probar el envío de correos, configura tu propia cuenta de Gmail con una [contraseña de aplicación](https://myaccount.google.com/apppasswords) y define ambas variables en tu entorno local o en la configuración de ejecución de tu IDE.
## Apéndices

### Licencia

Este proyecto se distribuye bajo la licencia MIT.

### Referencias

- Documentación oficial de Spring Boot: https://docs.spring.io/spring-boot/
- Documentación de Spring Security: https://docs.spring.io/spring-security/
- JJWT (Java JWT): https://github.com/jwtk/jjwt
