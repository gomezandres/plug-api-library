# plug-api-library

Cliente HTTP nativo, estándar de Flexibility, para llamar a otras APIs desde cualquier
microservicio Java. Construido únicamente sobre `java.net.http.HttpClient` (Java 25) — sin
dependencia de ningún framework (Spring, Quarkus, etc.), así que se usa igual en cualquier
proyecto.

## Instalación

```xml
<dependency>
    <groupId>com.plug</groupId>
    <artifactId>plug-api-library</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

No agrega dependencias de terceros de forma forzada. Los métodos JSON tipados (`Class<T>`)
usan Jackson por defecto, pero Jackson está declarado como dependencia `optional` — si tu
servicio ya lo tiene en el classpath (la gran mayoría) no hay que hacer nada; si no lo tiene y
nunca llamás a los métodos JSON, tampoco lo necesitás.

## Uso básico

```java
PlugHttpClient client = PlugHttpClient.builder()
    .baseUri("https://api.example.com")
    .connectTimeout(Duration.ofSeconds(5))
    .requestTimeout(Duration.ofSeconds(10))
    .defaultHeader("X-App-Name", "orders-service")
    .build();

// GET tipado
Order order = client.get("/orders/{id}").pathParam("id", 123).execute(Order.class);

// POST con body serializado a JSON automáticamente
Order created = client.post("/orders").body(new Order(0, "NEW")).execute(Order.class);

// Respuesta cruda (para health-checks, etc.)
PlugHttpResponse<String> health = client.get("/health").executeString();

// Async
CompletableFuture<Order> future = client.get("/orders/{id}").pathParam("id", 123).executeAsync(Order.class);

// Query params
client.get("/search").queryParam("q", "algo").executeString();

// No lanzar excepción en status no-2xx
var response = client.get("/orders/{id}").pathParam("id", 999).throwOnError(false).executeString();
```

`PlugHttpClient` es inmutable y thread-safe una vez construido: se instancia **una sola vez**
por servicio (por ejemplo como bean singleton) y se reutiliza, igual que
`java.net.http.HttpClient`.

## Manejo de errores

- Status no-2xx → `HttpStatusException` (con `statusCode()`, `rawBody()`, `headers()`), salvo
  que se use `.throwOnError(false)`.
- Timeouts (incluso después de agotar los reintentos) → `HttpTimeoutException`.
- Errores de red → `PlugHttpException`.
- Circuito abierto → `CircuitBreakerOpenException` (no llega a hacer la llamada de red).

## Reintentos

Por defecto **no reintenta** (comportamiento seguro). Para habilitarlo:

```java
PlugHttpClient client = PlugHttpClient.builder()
    .baseUri("https://api.example.com")
    .retryPolicy(ExponentialBackoffRetryPolicy.builder()
        .maxAttempts(3)
        .baseDelay(Duration.ofMillis(200))
        .retryableStatusCodes(Set.of(502, 503, 504))
        .build())
    .build();
```

Solo reintenta métodos idempotentes (GET, HEAD, PUT, DELETE, OPTIONS) ante errores de
red/timeout o los status codes configurados. POST/PATCH nunca se reintentan con la política
por defecto — si necesitás otro comportamiento, implementá tu propio `RetryPolicy`.

## Circuit breaker

Deshabilitado por defecto. Para habilitarlo (por host de destino):

```java
PlugHttpClient client = PlugHttpClient.builder()
    .baseUri("https://api.example.com")
    .circuitBreaker(CircuitBreakerConfig.builder()
        .failureThreshold(5)
        .openDuration(Duration.ofSeconds(30))
        .build())
    .build();
```

## JSON con otra librería (no Jackson)

```java
PlugHttpClient client = PlugHttpClient.builder()
    .baseUri("https://api.example.com")
    .jsonCodec(new MyGsonCodec()) // implementa com.plug.http.json.JsonCodec
    .build();
```

## Integración con el logging estandarizado de la empresa

**Este es el punto más importante para adoptar la librería en un microservicio real.**

La librería no depende de ninguna librería de logging (ni SLF4J, ni Log4j2, ni la librería
corporativa) — pero sí define, como estándar de la empresa, **qué se loguea y cuándo**. Por cada
intento de request (incluyendo reintentos) arma **dos** `HttpLogEvent`: uno `OUTBOUND` justo
antes de mandar el request, y uno `INBOUND` cuando llega la respuesta (o falla) de ese intento —
cada uno con su propio momento real, en vez de un único evento combinado después de que todo
terminó.

```java
public record HttpLogEvent(
    HttpLogLevel level, HttpLogPhase phase, String eventName, String correlationId, String method,
    URI uri, int attempt, boolean willRetry, int statusCode, long durationMillis, Throwable error,
    Map<String, String> headers, String body
) {}
```

- `phase` es `OUTBOUND` o `INBOUND`. En `OUTBOUND`, `headers`/`body` son los del request; en
  `INBOUND`, los de la respuesta. `statusCode`/`durationMillis`/`error` no se conocen todavía en
  `OUTBOUND` (`-1`/`0`/`null`).
- `correlationId` es estable entre ambas fases y entre reintentos de un mismo llamado lógico.
- `eventName` siempre es `"http.client.request"` (nombre fijo para filtrar/buscar).
- `level` es siempre `INFO` en `OUTBOUND` (todavía no hay resultado que evaluar). En `INBOUND` lo
  decide un `HttpLogLevelPolicy` — por defecto: éxito (2xx) → `INFO`, 4xx → `WARN`,
  5xx/timeout/error de red → `WARN` si todavía se va a reintentar, `ERROR` si ya es el intento
  final. Cada equipo puede reemplazar esta política con `.logLevelPolicy(...)` si necesita otro
  criterio.

Lo único que la aplicación tiene que hacer es implementar **dónde** termina ese evento —
`HttpLogSink`, una interfaz de un solo método:

```java
public interface HttpLogSink {
    void log(HttpLogEvent event);
}
```

Ejemplo de adaptador hacia la librería de logging corporativa (ejemplo ilustrativo —
**hay que confirmar los nombres de campo y el formato reales con el dueño de esa librería
antes de llevarlo a producción**):

```java
HttpLogSink corporateSink = event -> CorporateLogger.log(
    mapLevel(event.level()), // WARN/ERROR/INFO/DEBUG -> el nivel del logger corporativo
    event.eventName(),
    Map.of(
        "correlationId", event.correlationId(),
        "method", event.method(),
        "uri", event.uri().toString(),
        "attempt", event.attempt(),
        "statusCode", event.statusCode(),
        "durationMillis", event.durationMillis()
    ),
    event.error()
);

PlugHttpClient client = PlugHttpClient.builder()
    .baseUri("https://api.example.com")
    .logSink(corporateSink)
    .build();
```

Si no se configura ningún `logSink`, no se loguea nada (no-op) — es seguro por defecto. Un
`HttpLogSink` que lanza una excepción nunca rompe el pedido HTTP real (se captura y se ignora):
un bug en el logging no puede tirar abajo una llamada de producción.

La librería también trae `StandardHttpLogFormat.logAsStandardJson(event)`, un `HttpLogSink` ya
armado con el formato estándar de la empresa (envelope `level`/`thread`/`logger` + `message` con
`type`/`id`/`address`-o-`responseCode`/`method`/`headers`/`payload`), impreso por `System.out`.
Sirve para usar tal cual (`.logSink(StandardHttpLogFormat::logAsStandardJson)`) mientras no haya un
encoder JSON propio configurado — para producción, confirmar con el dueño de la librería de
logging corporativa cómo pasarle estos mismos campos a su encoder real.

## Correlation ID

Siempre se genera un `UUID` random (generador configurable vía `.correlationIdSupplier(...)`) que
identifica el llamado lógico en los `HttpLogEvent` y se mantiene estable entre reintentos. Mandarlo
como header en el request es opcional y está apagado por defecto — se activa con
`.sendCorrelationIdHeader(true)`, y el nombre del header (`X-Correlation-Id` por defecto) se
configura vía `.correlationIdHeader(...)`.

## Qué NO incluye (v1)

Multipart/file upload, bodies en streaming, tuning de connection pool más allá de lo que ya da
`HttpClient`, rate limiting/bulkhead, helpers de autenticación (usar `.defaultHeader(...)` o un
interceptor), emisión de métricas (dejarlo también al interceptor/librería de logging).
