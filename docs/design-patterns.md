# DevPulse design patterns

Written against commit `295a7f0`. At that commit, `metrics-service` has only its Spring Boot entry point and `analytics-service` has only `/health`; neither has feature code, so neither is credited with a pattern below.

DevPulse is a backend for developer-productivity analytics, split into services that accept webhooks, manage identity, and send alerts. Patterns give the three-person team repeatable boundaries and conventions, so independently written services still fit together.

Links below are repository-relative and point to the exact code discussed. “Framework supplied” means Spring, Spring Cloud Gateway, Spring AMQP, Spring Data, or JJWT implements the general mechanism; the team configured or extended it.

## Architectural patterns

### Microservices

**What it is (in plain words):** A system is split into small programs, each with one main job, instead of one large program.

**Where we used it:** [service containers and ports](infrastructure/docker/docker-compose.yml#L106-L229).

**Why we used it here:** Identity, webhook ingestion, metrics, alerts, and ML can be developed and deployed separately.

**How it works in our code:**
```yaml
  auth-service:
    <<: *service-defaults
    build:
      context: ../../backend
      dockerfile: auth-service/Dockerfile
    container_name: devpulse-auth-service
```
The compose file starts auth as its own container; the adjacent sections do the same for the other services. This is a team architecture decision, with Docker supplying the runtime isolation.

**What would break without it:** Changes to one concern would be coupled to every other concern in one deployable application.

### API Gateway

**What it is (in plain words):** One public door receives requests and sends each one to the right internal service.

**Where we used it:** [gateway routes](backend/api-gateway/src/main/resources/application.yml#L18-L51).

**Why we used it here:** Clients use one address while service addresses remain internal details.

**How it works in our code:**
```yaml
        - id: auth-service
          uri: ${AUTH_SERVICE_URI:http://localhost:8081}
          predicates:
            - Path=/api/auth/**,/api/users/**
          filters:
            - StripPrefix=1
```
Spring Cloud Gateway (framework supplied) matches the path, removes `/api`, and proxies the remaining request. The team selected the routes and targets.

**What would break without it:** Every client would need to know and secure several service URLs.

### Gateway Offloading

**What it is (in plain words):** Work common to many services is done once at the edge instead of copied into each endpoint.

**Where we used it:** [JWT gateway filter](backend/api-gateway/src/main/java/com/devpulse/gateway/filter/JwtAuthenticationFilter.java#L48-L92).

**Why we used it here:** The gateway validates a token and safely forwards identity context before routing.

**How it works in our code:**
```java
ServerHttpRequest mutated = forwarded
        .header(USER_ID_HEADER, String.valueOf(userId))
        .header(COMPANY_ID_HEADER, companyId != null ? String.valueOf(companyId) : "")
        .build();

return chain.filter(exchange.mutate().request(mutated).build());
```
The team wrote this `GlobalFilter`; Spring Cloud Gateway supplies the filter chain. Auth-service still checks JWTs itself, so this is defence in depth rather than the only check.

**What would break without it:** Each downstream service would need to repeat gateway-wide forwarding rules.

### Event-Driven / Publish–Subscribe

**What it is (in plain words):** A producer announces an event without calling each listener directly; subscribers choose the events they need.

**Where we used it:** [publisher](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L34-L43) and [notification bindings](backend/notification-service/src/main/java/com/devpulse/notification/config/RabbitMQConfig.java#L33-L45).

**Why we used it here:** Integration can publish an event without knowing which future services will react to it.

**How it works in our code:**
```java
String routingKey = event.getEventType();
log.info("Publishing event [{}] with eventId: {} to exchange '{}' using routing key '{}'",
        event.getClass().getSimpleName(), event.getEventId(), exchangeName, routingKey);

rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
```
The team chooses `eventType` as the routing key. Spring AMQP supplies `RabbitTemplate`, while RabbitMQ’s topic exchange routes `alert.#` and `pr.#` to notification’s queue.

**What would break without it:** Adding another consumer would require changing the webhook service to call it.

### Shared Database

**What it is (in plain words):** Several services use one PostgreSQL database rather than each owning a separate database.

**Where we used it:** [shared JDBC URL](infrastructure/docker/docker-compose.yml#L26-L29) and [single Flyway runner](infrastructure/docker/docker-compose.yml#L54-L70).

**Why we used it here:** It keeps operations manageable for a small project and supports the existing cross-domain foreign keys.

**How it works in our code:**
```yaml
# The shared database URL (JDBC form) reused by every Java service.
x-postgres-url: &postgres-url
  POSTGRES_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-devpulse}
```
The same compose anchor is injected into the Java services. This is a deliberate compromise: code-review ownership, rather than database permissions, limits writes.

**What would break without it:** Replacing it with database-per-service now would require migrations, synchronization, and redesign of cross-table links.

### Anti-Corruption Layer

**What it is (in plain words):** A small translator stops an outside system’s strange data shape from spreading through the rest of the application.

**Where we used it:** [webhook normalizer](backend/integration-service/src/main/java/com/devpulse/integration/service/WebhookEventNormalizer.java#L36-L47).

**Why we used it here:** GitHub and Jira payloads are converted into DevPulse’s own event classes before other services see them.

**How it works in our code:**
```java
JsonNode root = objectMapper.readTree(rawJson);
if ("github".equalsIgnoreCase(provider)) {
    return normalizeGithubEvent(eventType, companyId, root);
} else if ("jira".equalsIgnoreCase(provider)) {
    return normalizeJiraEvent(eventType, companyId, root);
}
```
The team wrote the conditional translator. `ObjectMapper` is framework/library supplied; its `JsonNode` lets the translator read external JSON without exposing it to consumers.

**What would break without it:** GitHub/Jira field names and changes would leak into every consumer.

### Layered (N-tier) Architecture

**What it is (in plain words):** HTTP handling, business rules, and database access have separate jobs.

**Where we used it:** [controller calling service](backend/auth-service/src/main/java/com/devpulse/auth/controller/AuthController.java#L41-L46), [service implementation](backend/auth-service/src/main/java/com/devpulse/auth/service/AuthServiceImpl.java#L60-L105), and [repository](backend/auth-service/src/main/java/com/devpulse/auth/repository/UserRepository.java#L11-L16).

**Why we used it here:** It keeps controllers thin and makes rules testable without putting SQL details in HTTP methods.

**How it works in our code:**
```java
public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```
The controller delegates rather than registering a user itself. This separation is team-written; Spring MVC supplies request binding and response handling.

**What would break without it:** Endpoint methods would accumulate validation, authentication, persistence, and mapping logic together.

### Audit Log

**What it is (in plain words):** Keep the original incoming event so later investigation can see what arrived.

**Where we used it:** [GitHub webhook flow](backend/integration-service/src/main/java/com/devpulse/integration/controller/WebhookController.java#L81-L92).

**Why we used it here:** A raw webhook can be checked when normalization or a provider payload causes a problem.

**How it works in our code:**
```java
RawEventLog rawLog = new RawEventLog(companyId, "github", eventType, payload);
rawEventLogRepository.save(rawLog);

saveOrUpdateRepo(companyId, payload);

BaseEvent event = normalizer.normalize("github", eventType, companyId, payload);
```
The raw payload is saved before derived records and publication. Spring Data supplies the `save` implementation; deciding to retain the raw event is a team choice.

**What would break without it:** A failed or disputed webhook would have no retained source payload to inspect.

## GoF patterns

### Adapter

**What it is (in plain words):** An adapter makes one interface fit another without changing either side.

**Where we used it:** [JSON message converter](backend/integration-service/src/main/java/com/devpulse/integration/config/RabbitMQConfig.java#L30-L37).

**Why we used it here:** Java event objects must become AMQP messages and back again.

**How it works in our code:**
```java
/**
 * Configures Spring AMQP to use Jackson JSON serialization instead of
 * default Java binary serialization when converting messages.
 */
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```
`Jackson2JsonMessageConverter` is a Spring AMQP-supplied Adapter: it fits Jackson JSON conversion to AMQP’s `MessageConverter` interface. The team explicitly selects it instead of default Java serialization.

**What would break without it:** Publishers and listeners would not share this JSON conversion boundary.

### Strategy

**What it is (in plain words):** Code calls a small interchangeable rule instead of hard-coding one rule everywhere.

**Where we used it:** [rate-limit key strategy](backend/api-gateway/src/main/java/com/devpulse/gateway/config/RateLimitConfig.java#L27-L40).

**Why we used it here:** Logged-in callers are limited by user ID and public callers by IP address.

**How it works in our code:**
```java
String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
if (userId != null && !userId.isBlank()) {
    return Mono.just("user:" + userId);
}
InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
```
The team provides one `KeyResolver` strategy as a lambda. Spring Cloud Gateway’s rate limiter calls that framework extension point.

**What would break without it:** The rate limiter would have no consistent caller key, or key selection would be mixed into route configuration.

### Template Method

**What it is (in plain words):** A parent class fixes the overall process while a child fills in one step.

**Where we used it:** [auth JWT filter callback](backend/auth-service/src/main/java/com/devpulse/auth/security/JwtAuthenticationFilter.java#L26-L42).

**Why we used it here:** Spring Security owns the once-per-request filter process, while DevPulse provides token handling.

**How it works in our code:**
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
```
`OncePerRequestFilter` (framework supplied) is the template; Spring invokes the overridden hook at the right point. The team supplies only the authentication step.

**What would break without it:** The service would have to recreate servlet filter lifecycle and “once” behavior.

### Chain of Responsibility

**What it is (in plain words):** A request travels through ordered handlers; each can act or pass it on.

**Where we used it:** [security chain configuration](backend/auth-service/src/main/java/com/devpulse/auth/config/SecurityConfig.java#L44-L62).

**Why we used it here:** JWT work must happen before Spring’s username/password filter and authorization checks.

**How it works in our code:**
```java
.authenticationProvider(authenticationProvider())
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

return http.build();
```
Spring Security supplies the chain. The team inserts its `JwtAuthenticationFilter` in the required order.

**What would break without it:** Authentication might happen after authorization, or every controller would need to inspect headers.

### Builder

**What it is (in plain words):** A builder assembles a complicated value step by step in readable order.

**Where we used it:** [JWT construction](backend/auth-service/src/main/java/com/devpulse/auth/security/JwtService.java#L46-L58).

**Why we used it here:** A token needs several claims, timestamps, and a signature without a long fragile constructor.

**How it works in our code:**
```java
return Jwts.builder()
        .subject(String.valueOf(user.getUserId()))
        .claim("email", user.getEmail())
        .claim("companyId", user.getCompany().getCompanyId())
        .claim("systemRole", user.getSystemRole())
        .issuedAt(now)
        .expiration(expiry)
        .signWith(signingKey)
        .compact();
```
JJWT supplies the builder API; the team selects the DevPulse claims and signing key.

**What would break without it:** Token construction would be harder to read and easier to mis-order.

### Factory Method

**What it is (in plain words):** A method creates the right object while callers depend on its general type.

**Where we used it:** [authentication provider factory](backend/auth-service/src/main/java/com/devpulse/auth/config/SecurityConfig.java#L65-L71).

**Why we used it here:** Spring needs an `AuthenticationProvider`, but the configuration decides its concrete DAO-based implementation.

**How it works in our code:**
```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder());
    return provider;
}
```
The team wrote this bean factory method. Spring IoC discovers it and injects the returned object by its interface.

**What would break without it:** Configuration users would be tied directly to construction details.

### Facade

**What it is (in plain words):** One simple class hides several lower-level steps behind a focused operation.

**Where we used it:** [event publisher service](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L13-L43).

**Why we used it here:** Controllers should say “publish this event,” not know exchange names or `RabbitTemplate` calls.

**How it works in our code:**
```java
public void publishEvent(BaseEvent event) {
    if (event == null) {
        throw new IllegalArgumentException("Cannot publish null event");
    }
    String routingKey = event.getEventType();
    rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
}
```
This is a team-written facade over the framework’s template. It centralizes the routing-key convention.

**What would break without it:** Each controller would repeat messaging details and could choose inconsistent routing keys.

### Observer

**What it is (in plain words):** A listener waits for a change or event and reacts when it happens.

**Where we used it:** [RabbitMQ listener](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L51-L61).

**Why we used it here:** Notification reacts to events without integration calling notification directly.

**How it works in our code:**
```java
@RabbitListener(queues = "${devpulse.rabbitmq.queue.notification:notification.events}")
public void handleIncomingEvent(BaseEvent event) {
    log.info("Notification Service received event [{}] with eventId: {}, eventType: {}",
            event.getClass().getSimpleName(), event.getEventId(), event.getEventType());

    if (event instanceof AlertPrHighRiskEvent highRiskEvent) {
```
Spring AMQP supplies the listener registration and invocation. The team supplies the observer’s reaction.

**What would break without it:** Notification would need a synchronous API call from every producer.

### Proxy

**What it is (in plain words):** A stand-in object performs work for another object while adding behavior around the call.

**Where we used it:** [repository declaration](backend/auth-service/src/main/java/com/devpulse/auth/repository/UserRepository.java#L11-L16).

**Why we used it here:** The code needs database operations without hand-writing a repository implementation.

**How it works in our code:**
```java
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
```
Spring Data creates a runtime proxy for this interface and derives query behavior from method names. The interface is team-written; the proxy is framework supplied.

**What would break without it:** The team would need concrete repository classes and manual query plumbing.

### Singleton

**What it is (in plain words):** One shared instance is used for an application-wide service.

**Where we used it:** [Spring component](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L10-L25).

**Why we used it here:** One configured publisher is enough for the service’s dependency graph.

**How it works in our code:**
```java
@Service
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public EventPublisherService(
            RabbitTemplate rabbitTemplate,
```
Spring’s default bean scope is singleton, so the container creates and shares one instance. This is framework lifecycle behavior; the team marks the class as a service.

**What would break without it:** Repeated construction could give components inconsistent configured collaborators.

## Enterprise patterns

### Repository

**What it is (in plain words):** A repository gives domain code collection-like methods for stored data.

**Where we used it:** [alert-rule repository use](backend/notification-service/src/main/java/com/devpulse/notification/service/AlertRuleService.java#L19-L39).

**Why we used it here:** Alert rules can be found and saved without service code writing SQL.

**How it works in our code:**
```java
public List<AlertRule> getRulesByCompany(Integer companyId) {
    return alertRuleRepository.findByCompanyIdAndIsActiveTrue(companyId);
}

public AlertRule createRule(AlertRule rule) {
    return alertRuleRepository.save(rule);
}
```
The service uses query and save methods on a repository interface. Spring Data supplies its implementation.

**What would break without it:** Persistence code would be scattered across services and controllers.

### Data Mapper

**What it is (in plain words):** A mapper copies data between an internal model and a data shape meant for another layer.

**Where we used it:** [user mapper](backend/auth-service/src/main/java/com/devpulse/auth/mapper/UserMapper.java#L19-L31).

**Why we used it here:** Authentication responses must not expose the whole JPA `User` entity.

**How it works in our code:**
```java
public AuthResponse toAuthResponse(User user, String token, long expiresIn) {
    return new AuthResponse(
            token,
            expiresIn,
            user.getUserId(),
            user.getEmail(),
            user.getFullName(),
            user.getSystemRole()
    );
}
```
This is a team-written mapper, not an automatic framework mapping. It selects only response fields.

**What would break without it:** Controllers or entities would take on API-shaping responsibility and risk leaking fields.

### DTO

**What it is (in plain words):** A DTO is a small object made specifically to carry request or response data.

**Where we used it:** [authentication response DTO](backend/auth-service/src/main/java/com/devpulse/auth/dto/AuthResponse.java#L7-L15) and [controller response](backend/auth-service/src/main/java/com/devpulse/auth/controller/AuthController.java#L41-L46).

**Why we used it here:** Login/register responses need token and selected identity fields, not the password hash or persistence relationships.

**How it works in our code:**
```java
private String accessToken;
private String tokenType;
private long expiresIn;
private Integer userId;
private String email;
private String fullName;
private String systemRole;
```
`AuthResponse` is a team-written transport shape. Spring MVC serializes it to HTTP JSON.

**What would break without it:** API responses would be tied to entity structure and could expose sensitive columns.

### Service Layer

**What it is (in plain words):** A service layer holds an application action that may coordinate several objects.

**Where we used it:** [registration service](backend/auth-service/src/main/java/com/devpulse/auth/service/AuthServiceImpl.java#L60-L105).

**Why we used it here:** Registration checks an email, resolves or creates a company, creates a user, persists it, makes a token, and maps a response.

**How it works in our code:**
```java
if (userRepository.existsByEmail(request.getEmail())) {
    throw new DuplicateEmailException(request.getEmail());
}

Company company;
boolean isAdmin = Boolean.TRUE.equals(request.getIsCompany())
        || (request.getCompanyName() != null && !request.getCompanyName().isBlank());
```
The team puts the workflow in `AuthServiceImpl`, leaving the controller as an HTTP adapter. Spring supplies the transaction interceptor for `@Transactional`.

**What would break without it:** The registration workflow would be duplicated or crowded into controller methods.

### Front Controller

**What it is (in plain words):** One framework entry point receives HTTP requests and dispatches them to controllers.

**Where we used it:** [REST controller annotations](backend/auth-service/src/main/java/com/devpulse/auth/controller/AuthController.java#L28-L30).

**Why we used it here:** All auth routes get the same Spring MVC request mapping, validation, and exception pipeline.

**How it works in our code:**
```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
```
Spring MVC’s `DispatcherServlet` is the actual Front Controller (framework supplied); these annotations register this team-written handler with it.

**What would break without it:** Each endpoint would need its own low-level HTTP parsing and routing.

### Dependency Injection / IoC

**What it is (in plain words):** A container creates objects and gives them the collaborators they need.

**Where we used it:** [constructor injection](backend/auth-service/src/main/java/com/devpulse/auth/service/AuthServiceImpl.java#L44-L58).

**Why we used it here:** The service can depend on repositories, security tools, and a mapper without constructing them itself.

**How it works in our code:**
```java
public AuthServiceImpl(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       ProjectMemberRepository projectMemberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       UserMapper userMapper) {
```
Spring IoC supplies the resolved beans to this constructor. The team declares dependencies explicitly, which also makes tests easier to arrange.

**What would break without it:** Classes would choose and construct concrete dependencies, making replacement and testing harder.

### Externalized Configuration

**What it is (in plain words):** Values that vary by machine or deployment live outside Java source.

**Where we used it:** [gateway configuration](backend/api-gateway/src/main/resources/application.yml#L8-L12) and [injected exchange name](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L21-L25).

**Why we used it here:** Redis, service URLs, secrets, and exchange names must differ between local and container runs.

**How it works in our code:**
```yaml
  # Backs the rate limiter. Counters must be shared: with in-memory state, two gateway
  # instances would each see half the traffic and the effective limit would double.
  data:
    redis:
      url: ${REDIS_URL:redis://localhost:6379}
```
Spring Boot resolves the environment variable and otherwise uses the shown local default. The configuration mechanism is framework supplied; choosing each property is a team decision.

**What would break without it:** Secrets and infrastructure addresses would be hard-coded into source or require recompiling per environment.

### Token Bucket Rate Limiting

**What it is (in plain words):** Requests spend tokens; tokens refill steadily, so short bursts are allowed but continuous overload is limited.

**Where we used it:** [gateway limiter configuration](backend/api-gateway/src/main/resources/application.yml#L22-L29).

**Why we used it here:** Public login and authenticated routes need a shared per-caller request budget.

**How it works in our code:**
```yaml
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userKeyResolver}"
                redis-rate-limiter.replenishRate: 10    # sustained requests/second
                redis-rate-limiter.burstCapacity: 20    # short spikes allowed up to this
                redis-rate-limiter.requestedTokens: 1
```
Spring Cloud Gateway’s Redis rate limiter supplies the token-bucket algorithm. The team selects its key resolver and limits; Redis keeps counters shared across gateway instances.

**What would break without it:** A single client could make unbounded requests and one gateway instance would not share limits with another.

### Health Check API

**What it is (in plain words):** A small endpoint tells a platform whether a service is alive.

**Where we used it:** [Actuator exposure](backend/auth-service/src/main/resources/application.yml#L22-L32).

**Why we used it here:** Docker/orchestration and developers need a standard liveness check.

**How it works in our code:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```
Spring Boot Actuator supplies `/actuator/health` and `/actuator/info`; the team exposes them. The gateway treats those paths as public at [its filter configuration](backend/api-gateway/src/main/java/com/devpulse/gateway/filter/JwtAuthenticationFilter.java#L28-L36).

**What would break without it:** Automation would need custom, inconsistent ways to determine service status.

## Notable design decisions

### Polymorphic auth exceptions with one handler

`BaseAuthException` stores an HTTP status ([lines 9–25](backend/auth-service/src/main/java/com/devpulse/auth/exception/BaseAuthException.java#L9-L25)); concrete domain exceptions inherit it. The single `@ExceptionHandler(BaseAuthException.class)` ([lines 19–28](backend/auth-service/src/main/java/com/devpulse/auth/exception/GlobalExceptionHandler.java#L19-L28)) therefore returns a consistent error response for duplicate-email, missing-resource, and credential domain failures. This is deliberate polymorphism: adding another domain exception does not require another controller handler.

### Gateway identity headers are unforgeable at the trust boundary

Before considering whether a path is public, the gateway removes both client-supplied identity headers ([lines 52–64](backend/api-gateway/src/main/java/com/devpulse/gateway/filter/JwtAuthenticationFilter.java#L52-L64)). Only after a verified JWT does it add values derived from claims ([lines 81–92](backend/api-gateway/src/main/java/com/devpulse/gateway/filter/JwtAuthenticationFilter.java#L81-L92)). Doing the removal first matters: otherwise a public webhook route could forward a forged `X-User-Id` to a downstream service.

### RabbitMQ event type information is a Java/Spring contract

`BaseEvent` is abstract and has ordinary fields only; it contains no `@JsonTypeInfo` or `@JsonSubTypes` annotation ([lines 8–23](backend/shared-contracts/src/main/java/com/devpulse/contracts/events/BaseEvent.java#L8-L23)). Both integration and notification explicitly use Spring AMQP’s `Jackson2JsonMessageConverter` ([publisher config](backend/integration-service/src/main/java/com/devpulse/integration/config/RabbitMQConfig.java#L34-L37), [consumer config](backend/notification-service/src/main/java/com/devpulse/notification/config/RabbitMQConfig.java#L43-L46)). That framework converter writes the concrete Java class name in the AMQP `__TypeId__` message header, so a listener declared as `BaseEvent` can receive the correct subclass. The payload itself does not carry a declared polymorphic JSON type; publisher and consumer therefore need identical shared-contracts classes, and a non-Java consumer such as analytics-service needs extra header/type mapping configuration before it can consume these messages.

## Patterns deliberately not used, and known gaps

- **Notification-channel Strategy is absent.** `NotificationEventListener` directly holds Slack, email, and webhook services ([lines 30–48](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L30-L48)) and calls each one in sequence ([lines 100–133](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L100-L133)). Introduce a `NotificationChannel` interface with `send(...)`, implement it per channel, and inject a collection keyed by channel name.
- **Polymorphic event dispatch is absent.** The listener uses an `instanceof` type switch ([lines 51–61](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L51-L61)). A Visitor, handler registry, or `BaseEvent` polymorphic dispatch method would let a new event type add its own handler without editing this chain.
- **Notification breaks the DTO boundary.** `AlertRuleController` accepts and returns the JPA `AlertRule` entity ([lines 22–38](backend/notification-service/src/main/java/com/devpulse/notification/controller/AlertRuleController.java#L22-L38)), unlike auth-service’s DTO plus mapper approach. Add `AlertRuleRequest`/`AlertRuleResponse` and an `AlertRuleMapper`, then expose only DTOs.
- **Circuit Breaker and Retry are absent.** Slack and webhook delivery catch exceptions and return `false` ([Slack lines 47–58](backend/notification-service/src/main/java/com/devpulse/notification/slack/SlackNotificationService.java#L47-L58)); no resilience policy retries transient failures or opens a circuit. Add Resilience4j-decorated client calls with bounded retry/backoff and a fallback that marks the notification pending/failed.
- **Dead Letter Queue is absent.** Notification has one durable queue and topic bindings ([lines 28–45](backend/notification-service/src/main/java/com/devpulse/notification/config/RabbitMQConfig.java#L28-L45)), but no dead-letter exchange/queue arguments. Add a DLX/DLQ and retry policy so poison messages are retained for inspection rather than repeatedly failing or being lost.
- **Idempotent Consumer is absent.** Every normalized event is assigned a UUID ([lines 50–53](backend/integration-service/src/main/java/com/devpulse/integration/service/WebhookEventNormalizer.java#L50-L53)), and the listener logs/acts on it ([lines 51–54](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L51-L54)), but it never records processed IDs. Add a `processed_events` table with a unique `event_id`, claim it transactionally before side effects, and skip duplicates.
- **Metrics and analytics feature patterns are absent.** The Java metrics module has only its application class, and analytics is outside this Java-source review with only `/health`; neither currently supplies DORA, prediction, consumer, or persistence feature code to document.

## Summary table

| Pattern | Category | Where used | One-line purpose |
| --- | --- | --- | --- |
| Microservices | Architectural | [compose](infrastructure/docker/docker-compose.yml#L106-L229) | Separates backend responsibilities into deployable services. |
| API Gateway | Architectural | [routes](backend/api-gateway/src/main/resources/application.yml#L18-L51) | Gives clients one entry point. |
| Gateway Offloading | Architectural | [JWT filter](backend/api-gateway/src/main/java/com/devpulse/gateway/filter/JwtAuthenticationFilter.java#L48-L92) | Handles edge authentication and trusted headers once. |
| Event-Driven / Publish–Subscribe | Architectural | [publisher](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L34-L43) | Decouples producers from consumers. |
| Shared Database | Architectural | [compose](infrastructure/docker/docker-compose.yml#L26-L70) | Shares one PostgreSQL schema and migration runner. |
| Anti-Corruption Layer | Architectural | [normalizer](backend/integration-service/src/main/java/com/devpulse/integration/service/WebhookEventNormalizer.java#L36-L47) | Converts provider JSON to DevPulse events. |
| Layered (N-tier) | Architectural | [auth controller](backend/auth-service/src/main/java/com/devpulse/auth/controller/AuthController.java#L41-L46) | Separates HTTP, workflow, and storage. |
| Audit Log | Architectural | [webhook controller](backend/integration-service/src/main/java/com/devpulse/integration/controller/WebhookController.java#L81-L92) | Retains incoming webhook payloads. |
| Adapter | GoF | [converter](backend/integration-service/src/main/java/com/devpulse/integration/config/RabbitMQConfig.java#L30-L37) | Fits Jackson conversion to AMQP messages. |
| Strategy | GoF | [key resolver](backend/api-gateway/src/main/java/com/devpulse/gateway/config/RateLimitConfig.java#L27-L40) | Chooses a caller key for rate limiting. |
| Template Method | GoF | [JWT filter](backend/auth-service/src/main/java/com/devpulse/auth/security/JwtAuthenticationFilter.java#L26-L42) | Lets DevPulse fill Spring’s filter hook. |
| Chain of Responsibility | GoF | [security config](backend/auth-service/src/main/java/com/devpulse/auth/config/SecurityConfig.java#L44-L62) | Orders authentication handlers. |
| Builder | GoF | [JWT service](backend/auth-service/src/main/java/com/devpulse/auth/security/JwtService.java#L46-L58) | Builds signed tokens step by step. |
| Factory Method | GoF | [security config](backend/auth-service/src/main/java/com/devpulse/auth/config/SecurityConfig.java#L65-L71) | Creates the configured authentication provider. |
| Facade | GoF | [publisher](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L34-L43) | Hides AMQP publication details. |
| Observer | GoF | [listener](backend/notification-service/src/main/java/com/devpulse/notification/service/NotificationEventListener.java#L51-L61) | Reacts to queued events. |
| Proxy | GoF | [repository](backend/auth-service/src/main/java/com/devpulse/auth/repository/UserRepository.java#L11-L16) | Provides generated database access. |
| Singleton | GoF | [publisher bean](backend/integration-service/src/main/java/com/devpulse/integration/service/EventPublisherService.java#L10-L25) | Shares one Spring service instance. |
| Repository | Enterprise | [alert-rule service](backend/notification-service/src/main/java/com/devpulse/notification/service/AlertRuleService.java#L19-L29) | Provides storage operations to the service layer. |
| Data Mapper | Enterprise | [user mapper](backend/auth-service/src/main/java/com/devpulse/auth/mapper/UserMapper.java#L19-L31) | Converts entities into API data. |
| DTO | Enterprise | [AuthResponse](backend/auth-service/src/main/java/com/devpulse/auth/dto/AuthResponse.java#L7-L15) | Carries selected response fields. |
| Service Layer | Enterprise | [AuthServiceImpl](backend/auth-service/src/main/java/com/devpulse/auth/service/AuthServiceImpl.java#L60-L105) | Coordinates a registration use case. |
| Front Controller | Enterprise | [AuthController](backend/auth-service/src/main/java/com/devpulse/auth/controller/AuthController.java#L28-L30) | Registers routes with Spring MVC’s dispatcher. |
| Dependency Injection / IoC | Enterprise | [constructor](backend/auth-service/src/main/java/com/devpulse/auth/service/AuthServiceImpl.java#L44-L58) | Supplies collaborators from the container. |
| Externalized Configuration | Enterprise | [gateway YAML](backend/api-gateway/src/main/resources/application.yml#L8-L12) | Keeps environment values out of source. |
| Token Bucket Rate Limiting | Enterprise | [gateway YAML](backend/api-gateway/src/main/resources/application.yml#L22-L29) | Limits sustained traffic while allowing bursts. |
| Health Check API | Enterprise | [auth YAML](backend/auth-service/src/main/resources/application.yml#L22-L32) | Exposes standard service status endpoints. |
