# Diferencias: plug-api-library vs plug-provider-library

Comparación entre `plug-api-library` (cliente HTTP nativo, agnóstico a framework) y
`plug-provider-library` (catálogo de conectores a las APIs internas de Flexibility).

## Qué es cada uno

- **`plug-api-library`**: cliente HTTP genérico y reutilizable, sin conocimiento de endpoints
  específicos, basado en `java.net.http.HttpClient` (Java 25), sin dependencia de framework.
  Cualquier equipo lo usa para llamar a cualquier API.
- **`plug-provider-library`**: un catálogo de ~522 archivos con conectores a las APIs internas
  propias de Flexibility (accounts, users, notifications, catalogs, blacklist, credentials,
  limits, otp, scheduler, transactions, clients, beneficiaries), con un DTO de
  request/response por operación. Es un SDK específico, no un cliente genérico.

## Diferencias principales

| Aspecto | plug-provider-library | plug-api-library |
|---|---|---|
| Propósito | Catálogo de conectores a APIs internas puntuales | Cliente HTTP genérico, sin endpoints predefinidos |
| Framework | 100% acoplado a Spring Boot 2.7 (`@Value`, `@ConfigurationProperties`, `@ConditionalOnProperty`, `@RefreshScope`, Spring Cache) | Cero dependencia de framework |
| Java | 11 | 25 |
| Transporte HTTP | `RestTemplate` (Spring, legacy/mantenimiento) sobre Apache HttpClient con pooling explícito (400/200 por ruta) | `java.net.http.HttpClient` nativo del JDK |
| TLS | `SSLConnectionSocketFactory` que **confía en cualquier certificado** (`TrustStrategy` que acepta todo + `NoopHostnameVerifier`) — antipatrón de seguridad | Validación TLS estándar del JDK, sin overrides |
| Retries / circuit breaker | Ninguno a nivel HTTP | Retry con backoff exponencial + circuit breaker por host, opt-in, sin librerías de terceros |
| Logging | `LoggerClientInterceptor` compartido: loguea el **body completo** de cada request/response por SLF4J a `INFO`, con redacción configurable de claves sensibles | `HttpLogEvent`/`HttpLogSink`/`HttpLogLevelPolicy`: solo metadata (method, uri, status, duración, correlationId, sin bodies), sink no-op por defecto, agnóstico a SLF4J/Log4j2/lo que sea |
| JSON | `ObjectMapper` manual + doble conversión (`exchange` a `Object.class`, después `convertValue`) | `JsonCodec` pluggable, deserialización directa al tipo pedido; Jackson es dependencia `optional` |
| Errores | Parsea el body a `ApiError` y tira `ProviderException`/`SecurityException` (401); 404 devuelve `null` implícitamente | `HttpStatusException` tipado con status/body/headers crudos para cualquier no-2xx, más `HttpTimeoutException`/`CircuitBreakerOpenException` |
| Auth | Flujo OAuth2 client-credentials hecho a mano en el conector `users`, token cacheado en Redis | Nada incorporado — solo headers estáticos o por-request |
| Cache | `@Cacheable` (Spring Cache/Redis) opt-in en algunos conectores | No tiene capa de cache |
| Configuración | Declarativa por `application.yml` (una entrada por conector) | 100% programática vía builder Java |
| Código | Lombok + ModelMapper + DTOs por endpoint | Records de Java planos, sin Lombok |
| DI | `@Autowired` por campo, atado al ciclo de vida de Spring | Sin DI framework — builder plano |
| Timeouts default | 60s conexión y respuesta | 10s conexión, 30s request |

## Puntos a tener en cuenta (no son bugs, son diferencias de diseño)

1. **`plug-api-library` no tiene un hook para mutar el request dinámicamente** (ej. inyectar
   un token que se refresca solo). Se sacó el `HttpInterceptor` genérico a favor del
   `HttpLogSink` (solo logging). Si algún microservicio necesita algo como el flujo OAuth2 de
   `plug-provider-library`, hoy no hay un punto de extensión para eso — habría que agregarlo o
   resolverlo con un `defaultHeader` estático + lógica propia.
2. **Logging de bodies vs. solo metadata**: `plug-provider-library` loguea el body completo
   (con redacción); `plug-api-library` nunca toca el body para logging (más seguro por
   default, pero menos visibilidad de payloads para debugging si algún equipo lo necesitara).
3. El trust-all-certs de `plug-provider-library` es una práctica insegura que
   `plug-api-library` no reproduce — vale la pena marcarlo ahí como algo a corregir, no como
   algo a imitar.
