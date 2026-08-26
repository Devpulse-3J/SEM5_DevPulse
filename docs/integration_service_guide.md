# DevPulse — Integration Service Technical & Architectural Guide

**Author:** Umaya (Computer Science & Engineering Undergraduate)  
**Service:** `integration-service`  
**Purpose:** External Webhook Ingestion, Signature Verification, Raw Event & Domain Entity Logging (`raw_event_log`, `repos`, `jira_issues`), Payload Normalization, and RabbitMQ Event Publishing.

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
     ┌──────────────────────────────┼──────────────────────────────┐
     │ 1. Validate Signature        │ 2. Persist Raw & Domain Log  │ 3. Normalize & Publish
     ▼                              ▼                              ▼
┌────────────────────────┐ ┌──────────────────────────────────┐ ┌────────────────────────────────┐
│Github & Jira Validators│ │RawEventLog, Repo & JiraIssue Repos│ │     WebhookEventNormalizer     │
│   (HMAC SHA-256 / Token)│ │           (PostgreSQL)           │ │(Jackson ObjectMapper Parsing)  │
└────────────────────────┘ └──────────────────────────────────┘ └───────────────┬────────────────┘
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
2. **Signature Verification:** `GithubSignatureValidator` validates GitHub's `X-Hub-Signature-256` header using HMAC SHA-256, while `JiraSignatureValidator` verifies Jira webhook secret headers.
3. **Raw Log & Domain Persistence:** The raw body string is saved to `raw_event_log`, repository details are upserted into `repos` via `RepoRepository`, and Jira issues are upserted into `jira_issues` via `JiraIssueRepository`.
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

#### Repositories (`RawEventLogRepository.java`, `RepoRepository.java`, `JiraIssueRepository.java`)
* **What they do:** Provide CRUD (Create, Read, Update, Delete) and derived query methods out of the box.
* **Key Java/Spring Concept:**
  * Extending `JpaRepository<T, ID>` automatically provides methods like `.save()`, `.findById()`, `.findAll()`, and derived query methods like `findByCompanyIdAndJiraKey(companyId, jiraKey)`.

---

### 2.2 Webhook Ingestion & Security

#### `GithubSignatureValidator.java`
* **What it does:** Verifies that incoming GitHub webhooks actually originated from GitHub and were not forged by an attacker.
* **Key Java Concepts:**
  * `Mac.getInstance("HmacSHA256")`: Standard Java Cryptography Architecture (JCA) class used to generate cryptographic signatures.
  * `SecretKeySpec`: Converts raw secret string bytes into a cryptographic secret key object.

#### `JiraSignatureValidator.java`
* **What it does:** Verifies incoming Jira webhook tokens/signatures against configured `jira.webhook.secret`.

#### `WebhookController.java`
* **What it does:** Exposes HTTP endpoints for GitHub (`/webhooks/github`) and Jira (`/webhooks/jira`), validates signatures, saves raw logs and domain entities (`repos`, `jira_issues`), normalizes payloads, and publishes canonical events to RabbitMQ.

---

### 2.3 RabbitMQ Infrastructure Configuration

#### `RabbitMQConfig.java`
* **What it does:** Configures the RabbitMQ exchange and message conversion format when `integration-service` starts up.
* **Key Concepts:**
  * `@Configuration`: Indicates that this class defines bean configuration methods.
  * `@Bean`: Registers managed Singleton beans in the Spring container.
  * `TopicExchange`: Declares a RabbitMQ exchange named `devpulse.events` that routes messages using wildcard routing keys.
  * `Jackson2JsonMessageConverter`: Serializes Java objects as clean UTF-8 JSON.

---

### 2.4 Event Publishing Service

#### `EventPublisherService.java`
* **What it does:** Publishes canonical event objects to RabbitMQ.

---

### 2.5 Event Normalization Engine

#### `WebhookEventNormalizer.java`
* **What it does:** Converts unstructured raw JSON strings from GitHub or Jira webhooks into strongly typed canonical `BaseEvent` Java objects.

---

## 3. Quick Revision Cheat Sheet for Evaluations

| Concept | Explanation | Real Example in `integration-service` |
| :--- | :--- | :--- |
| **Dependency Injection (IoC)** | Spring automatically creates objects and passes them to constructors. | `WebhookController` receives `RawEventLogRepository`, `RepoRepository`, `JiraIssueRepository`, `GithubSignatureValidator`, `JiraSignatureValidator`, `WebhookEventNormalizer`, and `EventPublisherService`. |
| **Polymorphism** | Ability for different objects to be treated as their parent type. | `publishEvent(BaseEvent event)` accepts `PrOpenedEvent`, `IssueUpdatedEvent`, or `CommitPushedEvent` transparently. |
| **HMAC SHA-256** | Cryptographic hash algorithm used to verify payload integrity and origin. | [GithubSignatureValidator.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/integration-service/src/main/java/com/devpulse/integration/github/GithubSignatureValidator.java) and [JiraSignatureValidator.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/integration-service/src/main/java/com/devpulse/integration/jira/JiraSignatureValidator.java) compare signatures. |
| **Jackson JSON Parsing** | Library that parses raw JSON strings into Java object trees. | [WebhookEventNormalizer.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/integration-service/src/main/java/com/devpulse/integration/service/WebhookEventNormalizer.java) uses `ObjectMapper` and `JsonNode`. |
| **Spring Data JPA** | Data access framework eliminating boilerplate SQL. | `RawEventLogRepository`, `RepoRepository`, and `JiraIssueRepository` extend `JpaRepository` to persist entities in PostgreSQL. |

---

## 4. Testing Strategy (JUnit 5 + Mockito)

We built unit tests using **JUnit 5** and **Mockito**:
* **`WebhookControllerTest`**: Uses Mockito stubs (`mock()`, `when()`, `verify()`) to test signature verification, raw event logging, domain entity persistence (`Repo`, `JiraIssue`), and event publishing triggers.
* **`GithubSignatureValidatorTest` & `JiraSignatureValidatorTest`**: Test HMAC SHA-256 and secret token validation.
* **`RabbitMQConfigTest`**: Tests exchange initialization and bean configuration.
* **`EventPublisherServiceTest`**: Uses Mockito `ArgumentCaptor` to verify message publishing.
* **`WebhookEventNormalizerTest`**: Tests parsing and conversion for webhook payload formats.

Run all tests anytime via:
```bash
mvn test -pl integration-service -am
```
