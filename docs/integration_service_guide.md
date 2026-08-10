# DevPulse — Integration Service Technical & Architectural Guide

**Author:** Umaya (Computer Science & Engineering Undergraduate)  
**Service:** `integration-service`  
**Purpose:** External Webhook Ingestion, Signature Verification, Raw Event Logging, Payload Normalization, and RabbitMQ Event Publishing.

---

## 1. High-Level Architecture & End-to-End Flow

The `integration-service` acts as the primary gateway for external developer events coming from GitHub and Jira into the DevPulse microservice platform.

### Complete Webhook Ingestion Pipeline

```text
               ┌─────────────────────────────────────────┐
               │    GitHub / Jira Webhook POST           │
               └────────────────────┬────────────────────┘
                                    │
                                    ▼
             ┌─────────────────────────────────────────────┐
             │              WebhookController              │
             └──────────────────────┬──────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    │ 1. Validate Signature         │ 2. Save Raw Log               │ 3. Normalize & Publish
    ▼                               ▼                               ▼
┌───────────────────────┐ ┌───────────────────┐ ┌────────────────────────────────┐
│GithubSignatureValidator│ │RawEventLogRepository│ │     WebhookEventNormalizer     │
│    (HMAC SHA-256)     │ │   (PostgreSQL)    │ │(Jackson ObjectMapper Parsing)  │
└───────────────────────┘ └───────────────────┘ └───────────────┬────────────────┘
                                                                │ Returns BaseEvent
                                                                ▼
                                                ┌────────────────────────────────┐
                                                │     EventPublisherService      │
                                                │   (Spring AMQP RabbitTemplate) │
                                                └───────────────┬────────────────┘
                                                                │
                                                                ▼
                                                ┌────────────────────────────────┐
                                                │    RabbitMQ Topic Exchange     │
                                                │       ("devpulse.events")      │
                                                └────────────────────────────────┘
```

1. **HTTP Ingestion:** GitHub or Jira sends a `POST` request to `/webhooks/github` or `/webhooks/jira`.
2. **Signature Verification:** `GithubSignatureValidator` validates GitHub's `X-Hub-Signature-256` header using HMAC SHA-256 to ensure authenticity.
3. **Raw Log Persistence:** The raw body string and metadata are saved to PostgreSQL via `RawEventLogRepository`.
4. **Event Normalization:** `WebhookEventNormalizer` parses the raw JSON string using Jackson's `ObjectMapper` and converts it into a canonical Java event object (`PrOpenedEvent`, `IssueUpdatedEvent`, etc.).
5. **RabbitMQ Event Publishing:** `EventPublisherService` publishes the event object to the `devpulse.events` Topic Exchange via `RabbitTemplate`, serialized automatically as JSON using `Jackson2JsonMessageConverter`.

---

## 2. Component Deep Dive & Java/Spring Concepts

### 2.1 Database & Persistence Layer (JPA / Hibernate)

#### Entities (`RawEventLog.java`, `Repo.java`, `JiraIssue.java`)
* **What they do:** Represent tables in the PostgreSQL database (`raw_event_log`, `repos`, `jira_issues`).
* **Key Java/Spring Concepts:**
  * `@Entity`: Marks a Java class as a JPA entity mapped to a database table.
  * `@Table(name = "raw_event_log")`: Specifies the exact database table name.
  * `@Id` & `@GeneratedValue(strategy = GenerationType.IDENTITY)`: Marks the primary key and configures auto-increment sequence generation.
  * `@Column`: Maps class fields to database columns and enforces non-null or length constraints.

#### Repositories (`RawEventLogRepository.java`, etc.)
* **What they do:** Provide CRUD (Create, Read, Update, Delete) operations out of the box.
* **Key Java/Spring Concept:**
  * Extending `JpaRepository<RawEventLog, Integer>` automatically provides methods like `.save()`, `.findById()`, `.findAll()`, and `.deleteById()` without writing a single line of SQL!

---

### 2.2 Webhook Ingestion & Security

#### `GithubSignatureValidator.java`
* **What it does:** Verifies that incoming GitHub webhooks actually originated from GitHub and were not forged by an attacker.
* **How HMAC SHA-256 Works:**
  ```text
  GitHub:      payload + secret  ==> HMAC-SHA256 ==> X-Hub-Signature-256
  DevPulse:    payload + secret  ==> HMAC-SHA256 ==> expectedSignature
  Comparison:  expectedSignature.equals(signatureFromHeader)
  ```
* **Key Java Concepts:**
  * `Mac.getInstance("HmacSHA256")`: Standard Java Cryptography Architecture (JCA) class used to generate cryptographic signatures.
  * `SecretKeySpec`: Converts raw secret string bytes into a cryptographic secret key object.

#### `WebhookController.java`
* **What it does:** Exposes HTTP endpoints for GitHub (`/webhooks/github`) and Jira (`/webhooks/jira`).
* **Key Spring MVC Annotations:**
  * `@RestController`: Combines `@Controller` and `@ResponseBody`. Tells Spring Boot that methods in this class return HTTP response bodies directly (JSON/Map), not HTML views.
  * `@RequestMapping("/webhooks")`: Defines the base URI path for all endpoints in the controller.
  * `@PostMapping("/github")`: Maps HTTP `POST` requests sent to `/webhooks/github` to this method.
  * `@RequestBody`: Automatically binds the incoming HTTP request payload body to a Java String parameter.
  * `@RequestHeader`: Extracts specific HTTP headers (e.g. `X-GitHub-Event`, `X-Hub-Signature-256`, `X-Company-Id`).
  * `ResponseEntity<?>`: A Spring wrapper representing the full HTTP response (status code, headers, and body).

---

### 2.3 RabbitMQ Infrastructure Configuration

#### `RabbitMQConfig.java`
* **What it does:** Configures the RabbitMQ exchange and message conversion format when `integration-service` starts up.
* **Key Concepts:**
  * `@Configuration`: Indicates that this class defines bean configuration methods.
  * `@Bean`: Tells Spring: *"Execute this method, take the returned object, and register it as a managed Singleton bean in the Spring container."*
  * `@Value("${devpulse.rabbitmq.exchange:devpulse.events}")`: Injects a property value from `application.yml` with a fallback default.
  * `TopicExchange`: Declares a RabbitMQ exchange named `devpulse.events` that routes messages to queues based on wildcard routing keys (e.g., `pr.*`, `issue.*`).
  * `Jackson2JsonMessageConverter`: Overrides Spring AMQP's default binary serialization to serialize Java objects as clean, human-readable UTF-8 JSON.

---

### 2.4 Event Publishing Service

#### `EventPublisherService.java`
* **What it does:** Publishes canonical event objects to RabbitMQ.
* **Key Java & Spring Concepts:**
  * `@Service`: Marks the class as a business service bean in the Spring container.
  * **Constructor Injection:** Injects `RabbitTemplate` and exchange name via constructor (Spring best practice over `@Autowired` field injection).
  * `RabbitTemplate`: Spring AMQP's core helper class for interacting with RabbitMQ. Calling `rabbitTemplate.convertAndSend(exchange, routingKey, event)` converts the Java event to JSON and publishes it to the specified exchange.
  * **Polymorphism:** The method signature `publishEvent(BaseEvent event)` accepts *any* subclass of `BaseEvent` (`PrOpenedEvent`, `CommitPushedEvent`, etc.). It calls `event.getEventType()` dynamically to obtain the routing key!

---

### 2.5 Event Normalization Engine

#### `WebhookEventNormalizer.java`
* **What it does:** Converts unstructured raw JSON strings from GitHub or Jira webhooks into strongly typed canonical `BaseEvent` Java objects.
* **Key Java & Jackson Concepts:**
  * `@Component`: Marks the class as a generic Spring-managed bean.
  * `ObjectMapper`: The core class in the Jackson library used to parse, read, and write JSON.
  * `objectMapper.readTree(rawJson)`: Parses JSON string into a tree structure (`JsonNode`), allowing safe field traversal without crashing if optional fields are missing.
  * `root.path("pull_request").path("title").asText("")`: Safe node navigation. If any key in the path does not exist, `.path()` returns a `MissingNode` instead of throwing a `NullPointerException`!

#### Shared Event Classes (`shared-contracts`)
* `BaseEvent`: Abstract parent class holding common metadata (`eventId`, `companyId`, `projectId`, `eventType`, `timestamp`).
* `PrOpenedEvent`, `PrMergedEvent`, `PrClosedEvent`: Represent GitHub Pull Request lifecycle changes.
* `CommitPushedEvent`: Represents code push events.
* `DeploymentCreatedEvent`: Represents build/deployment status updates.
* `IssueUpdatedEvent`: Represents Jira ticket updates.

---

## 3. Quick Revision Cheat Sheet for Evaluations

| Concept | Explanation | Real Example in `integration-service` |
| :--- | :--- | :--- |
| **Dependency Injection (IoC)** | Spring automatically creates objects and passes them to constructors. | `WebhookController` automatically receives `RawEventLogRepository`, `GithubSignatureValidator`, `WebhookEventNormalizer`, and `EventPublisherService`. |
| **Polymorphism** | Ability for different objects to be treated as their parent type. | `publishEvent(BaseEvent event)` accepts `PrOpenedEvent`, `IssueUpdatedEvent`, or `CommitPushedEvent` transparently. |
| **HMAC SHA-256** | Cryptographic hash algorithm used to verify payload integrity and origin. | [GithubSignatureValidator.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/integration-service/src/main/java/com/devpulse/integration/github/GithubSignatureValidator.java) compares expected vs received signature headers. |
| **Jackson JSON Parsing** | Library that parses raw JSON strings into Java object trees. | [WebhookEventNormalizer.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/integration-service/src/main/java/com/devpulse/integration/service/WebhookEventNormalizer.java) uses `ObjectMapper` and `JsonNode`. |
| **Topic Exchange** | RabbitMQ routing mechanism supporting wildcard pattern matches. | `devpulse.events` exchange routes messages based on routing keys like `pr.opened` or `issue.updated`. |
| **Spring Data JPA** | Data access framework eliminating boilerplate SQL. | `RawEventLogRepository` extends `JpaRepository` to save logs to PostgreSQL with `.save()`. |

---

## 4. Testing Strategy (JUnit 5 + Mockito)

We built unit tests using **JUnit 5** and **Mockito**:
* **`WebhookControllerTest`**: Uses Mockito stubs (`mock()`, `when()`, `verify()`) to test signature verification, raw event logging, and event publishing triggers without needing a live web server.
* **`RabbitMQConfigTest`**: Tests exchange initialization and bean configuration.
* **`EventPublisherServiceTest`**: Uses Mockito `ArgumentCaptor` to capture and verify the exact exchange name, routing key (`pr.opened`), and payload passed to `RabbitTemplate`.
* **`WebhookEventNormalizerTest`**: Tests parsing and conversion for 6 different webhook payload formats.

Run all tests anytime via:
```bash
mvn test -pl integration-service -am
```

Current test status: **19/19 tests passing (`BUILD SUCCESS`)**!
