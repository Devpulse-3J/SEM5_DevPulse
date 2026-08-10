# DevPulse — Notification Service Technical & Architectural Guide

**Author:** Umaya (Computer Science & Engineering Undergraduate)  
**Service:** `notification-service`  
**Purpose:** Event Consumption from RabbitMQ, Alert Rule Management, Alert Evaluation & Generation, Multi-Channel Notification Dispatching (Slack, Email), and PostgreSQL Persistence (`alerts`, `notifications`, `alert_rules`).

---

## 1. High-Level Architecture & End-to-End Flow

The `notification-service` is the central alerting and notification engine for the DevPulse platform. It operates asynchronously by consuming developer events broadcast over RabbitMQ, evaluating incoming high-risk alerts against configured alert rules, saving alert audit trails to PostgreSQL, and dispatching notifications to team channels (Slack and Email).

### Complete Notification & Alert Pipeline

```text
               ┌─────────────────────────────────────────┐
               │         RabbitMQ Topic Exchange         │
               │           ("devpulse.events")           │
               └────────────────────┬────────────────────┘
                                    │ Wildcard Bindings: alert.#, pr.#
                                    ▼
              ┌───────────────────────────────────────────┐
              │            notification.events            │
              │              (RabbitMQ Queue)             │
              └─────────────────────┬─────────────────────┘
                                    │
                                    ▼
             ┌─────────────────────────────────────────────┐
             │          NotificationEventListener          │
             │              (@RabbitListener)              │
             └──────────────────────┬──────────────────────┘
                                    │
     ┌──────────────────────────────┼──────────────────────────────┐
     │ 1. Persist Alert Record      │ 2. Dispatch Slack            │ 3. Dispatch Email & Audit Log
     ▼                              ▼                              ▼
┌─────────────────────────┐ ┌────────────────────────┐ ┌──────────────────────────────────┐
│ Alert & AlertRepository │ │SlackNotificationService│ │     EmailNotificationService     │
│       (PostgreSQL)      │ │      (RestTemplate)    │ │        (JavaMailSender)          │
└─────────────────────────┘ └────────────────────────┘ └─────────────────┬────────────────┘
                                                                         │
                                                                         ▼
                                                       ┌──────────────────────────────────┐
                                                       │    Notification & Repository     │
                                                       │ (Persisted in PostgreSQL: 'sent')│
                                                       └──────────────────────────────────┘
```

1. **Async Queue Consumption:** The `@RabbitListener` method in `NotificationEventListener` listens continuously on queue `notification.events`.
2. **Polymorphic Event Processing:** When an `AlertPrHighRiskEvent` arrives from RabbitMQ, the service extracts risk scores, model algorithm details, and affected PR identifiers.
3. **Alert Persistence:** An `Alert` entity is instantiated with severity level `critical` or `warning` and persisted into PostgreSQL via `AlertRepository`.
4. **Multi-Channel Dispatching:**
   * `SlackNotificationService` formats a structured payload and dispatches it via HTTP `POST` using `RestTemplate` to Slack Incoming Webhooks.
   * `EmailNotificationService` formats email notifications and sends them using `JavaMailSender`.
5. **Notification Audit Logging:** A `Notification` record is created and stored in PostgreSQL with status set to `'sent'` (or `'failed'`) and timestamped with `sent_at`.

---

## 2. Component Deep Dive & Java/Spring Concepts

### 2.1 Database & Persistence Layer (JPA / Hibernate)

#### Entities (`AlertRule.java`, `Alert.java`, `Notification.java`)
* **What they do:** Map Java domain objects directly to PostgreSQL tables (`alert_rules`, `alerts`, `notifications`).
* **Key Annotations:**
  * `@Entity`: Registers the class as a JPA persistent entity.
  * `@Table(name = "alerts")`: Maps class to database table `alerts`.
  * `@Id` & `@GeneratedValue(strategy = GenerationType.IDENTITY)`: Marks primary key and auto-increment identity sequence.
  * `@Column(name = "triggered_at")`: Maps fields to database columns with nullability constraints.

#### Repositories (`AlertRuleRepository.java`, `AlertRepository.java`, `NotificationRepository.java`)
* **What they do:** Perform database queries without explicit SQL.
* **Key Features:**
  * Extending `JpaRepository<Alert, Integer>` provides `.save()`, `.findById()`, `.findAll()`, and `.deleteById()`.
  * Derived Spring Data JPA query methods like `findByCompanyIdAndIsActiveTrue(Integer companyId)` automatically generate SQL queries based on method names!

---

### 2.2 Async Event Listener & RabbitMQ Consumer Infrastructure

#### `RabbitMQConfig.java`
* **What it does:** Configures the RabbitMQ queue, exchange bindings, and Jackson JSON deserializer for `notification-service`.
* **Key Beans:**
  * `notificationQueue()`: Declares durable queue `notification.events`.
  * `bindingAlertEvents()`: Binds `notification.events` queue to `devpulse.events` exchange with routing key wildcard `alert.#`.
  * `bindingPrEvents()`: Binds queue with wildcard `pr.#`.
  * `jsonMessageConverter()`: Uses `Jackson2JsonMessageConverter` to deserialize incoming RabbitMQ JSON messages into Java event objects using class headers (`__TypeId__`).

#### `NotificationEventListener.java`
* **What it does:** Asynchronously receives and processes events from RabbitMQ.
* **Key Spring AMQP Annotations:**
  * `@Component`: Managed Spring bean.
  * `@RabbitListener(queues = "${devpulse.rabbitmq.queue.notification:notification.events}")`: Tells Spring AMQP to automatically listen to the queue and execute `handleIncomingEvent(BaseEvent event)` when a message arrives.
  * **Pattern Matching with `instanceof`:**
    ```java
    if (event instanceof AlertPrHighRiskEvent highRiskEvent) {
        // Handle high risk PR alert, save Alert & Notification entities
    } else if (event instanceof PrOpenedEvent prEvent) {
        // Log PR opened metric event
    }
    ```

---

### 2.3 Multi-Channel Delivery Services

#### `SlackNotificationService.java`
* **What it does:** Formats alert messages and dispatches HTTP POST requests to Slack Incoming Webhook endpoints.
* **Key Concepts:**
  * `RestTemplate`: Spring's HTTP client helper for making REST requests. Calling `restTemplate.postForEntity(webhookUrl, requestPayload, String.class)` sends the alert message payload to Slack.
  * **Fallback Handling:** If a webhook URL is unconfigured during local development, the service logs a simulated delivery without throwing exceptions.

#### `EmailNotificationService.java`
* **What it does:** Formats and sends email notifications.
* **Key Concepts:**
  * `JavaMailSender`: Spring Boot's mail abstraction for sending SMTP emails.
  * `SimpleMailMessage`: Represents basic email attributes (`setTo`, `setSubject`, `setText`, `setFrom`).

---

### 2.4 REST API Controller & Service Layer (Alert Rules)

#### `AlertRuleService.java`
* **What it does:** Business logic layer for managing user-defined alert rules.
* **Key Operations:**
  * `getRulesByCompany(Integer companyId)`: Fetches active alert rules for a specific tenant.
  * `createRule(AlertRule rule)`: Validates and saves a new alert rule.
  * `deleteRule(Integer ruleId)`: Soft-deactivates an alert rule (`isActive = false`).

#### `AlertRuleController.java`
* **What it does:** Exposes RESTful HTTP endpoints for frontend and API clients to manage alert rules.
* **Key REST Endpoints:**
  * `GET /api/alerts/rules?companyId=1` ➔ List all active alert rules (`200 OK`).
  * `GET /api/alerts/rules/{id}` ➔ Retrieve specific rule by ID (`200 OK` or `404 Not Found`).
  * `POST /api/alerts/rules` ➔ Create a new alert rule (`201 Created`).
  * `DELETE /api/alerts/rules/{id}` ➔ Deactivate an alert rule (`204 No Content`).

---

## 3. Quick Revision Cheat Sheet for Evaluations

| Concept | Explanation | Real Example in `notification-service` |
| :--- | :--- | :--- |
| **`@RabbitListener`** | Asynchronously consumes messages from a RabbitMQ queue. | `NotificationEventListener.handleIncomingEvent(BaseEvent event)` listens on `notification.events`. |
| **Derived JPA Queries** | Spring Data automatically generates SQL from method names. | `AlertRuleRepository.findByCompanyIdAndIsActiveTrue(companyId)` queries active rules for a company. |
| **`RestTemplate`** | Spring's HTTP client for making REST API calls to external services. | `SlackNotificationService.sendSlackNotification()` posts alerts to Slack webhooks. |
| **Polymorphic Event Handling** | Handles different event types using common base class inheritance. | `handleIncomingEvent(BaseEvent event)` receives `AlertPrHighRiskEvent`, `PrOpenedEvent`, or `PrMergedEvent`. |
| **Soft Deletion Pattern** | Marks records inactive instead of deleting rows from database. | `AlertRuleService.deleteRule()` sets `rule.setActive(false)` to preserve audit history. |
| **REST Controller** | Exposes HTTP JSON endpoints for frontend integrations. | [AlertRuleController.java](file:///c:/Users/user/Documents/GitHub/SEM5_DevPulse/backend/notification-service/src/main/java/com/devpulse/notification/controller/AlertRuleController.java) handles `/api/alerts/rules`. |

---

## 4. Testing Strategy (JUnit 5 + Mockito)

Unit tests built using **JUnit 5**, **Mockito**, and **Spring Boot Test MockMvc**:
* **`AlertRuleControllerTest`**: Uses `@WebMvcTest` and `MockMvc` to test REST API HTTP endpoints (`GET`, `POST`, `DELETE`).
* **`AlertRuleServiceTest`**: Tests business logic with Mockito repository mocks (`when().thenReturn()`, `verify()`).
* **`NotificationEventListenerTest`**: Mocks `AlertRepository`, `NotificationRepository`, `SlackNotificationService`, and `EmailNotificationService` to verify async message processing and database persistence triggers.
* **`SlackNotificationServiceTest` & `EmailNotificationServiceTest`**: Verify multi-channel notification dispatchers.

Run all tests anytime via:
```bash
mvn test -pl notification-service -am
```

Current test status: **14/14 tests passing (`BUILD SUCCESS`)**!
