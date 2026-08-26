# Testing in DevPulse — the ideas behind what we built

A guide to *why* each kind of test exists, which Spring Boot tool matches which
job, and what we actually added to this repo.

---

## 1. The core idea: how much of the app do you start?

Every testing decision in Spring Boot comes down to one question:

> **How much of the application do I need running to prove this?**

Starting more gives you more confidence but costs time and drags in
infrastructure. Starting less is fast and isolated but proves less. The whole
skill is picking the smallest thing that still proves what you care about.

```
        SLOW, most realistic
   ┌─────────────────────────────┐
   │  @SpringBootTest            │  whole app context
   │  + RANDOM_PORT              │  real HTTP server
   ├─────────────────────────────┤
   │  @SpringBootTest            │  whole context, fake HTTP
   │  + @AutoConfigureMockMvc    │
   ├─────────────────────────────┤
   │  @WebMvcTest / @DataJpaTest │  one slice only
   ├─────────────────────────────┤
   │  plain JUnit + Mockito      │  one class, nothing started
   └─────────────────────────────┘
        FAST, least realistic
```

Most tests should live at the bottom. A few at the top. This is the "testing
pyramid" — and our repo follows it: 61 tests, of which only 4 start a full
context.

---

## 2. The tools, and when each one is right

### Plain JUnit + Mockito — no Spring at all

```java
@ExtendWith(MockitoExtension.class)
class WebhookEventNormalizerTest {
    @Mock private SomeCollaborator collaborator;
```

Spring never starts. You construct the class yourself and hand it fakes.
Milliseconds per test.

**Use it for logic**: parsing, validation, calculations, mapping. If a class has
no framework dependency, it needs no framework to test.

Most of `integration-service` and `notification-service`'s existing tests are
this kind, which is why their whole suite finishes in under 4 seconds.

### `@WebMvcTest` — the web slice

```java
@WebMvcTest(AlertRuleController.class)
class AlertRuleControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean  private AlertRuleService alertRuleService;
```

Starts **only** the web layer: your controller, JSON conversion, validation,
error handling. No database, no repositories, no RabbitMQ.

**Use it to test HTTP behaviour**: status codes, JSON shape, request mapping.
The service underneath is replaced by `@MockBean`, so you control exactly what
it returns.

### `@DataJpaTest` — the persistence slice

Starts JPA and repositories, nothing else. **We do not use this**, because it
needs a real database schema — and ours is owned by Flyway against Postgres.
Adding it would mean Testcontainers, which we deliberately avoided.

### `@SpringBootTest` — the whole context

```java
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {
    @Test void contextLoads() { }
}
```

Starts **everything**: all beans, all auto-configuration, all your config files.

That empty test body looks pointless. It is not — it is the highest
value-per-line test in the repo. It fails when:

- a bean is missing or can't be constructed
- two beans depend on each other in a cycle
- a `@Value` property has no default and nothing supplies it
- a component scan misses a package
- any config file is malformed

These are exactly the failures that otherwise appear **at deploy time**, not
build time. One empty method catches all of them.

### `@SpringBootTest` + `@AutoConfigureMockMvc` — endpoints with real security

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @MockBean private AuthService authService;
```

Full context, but HTTP is simulated rather than going over a socket.

**Why not `@WebMvcTest` here?** Because we wanted to test the *real*
`SecurityConfig` filter chain — that `/auth/me` is actually rejected without a
token. A slice test would not load the production security rules, so it would
prove nothing about them. This is the difference between testing your
controller and testing your **security**.

### `@SpringBootTest(RANDOM_PORT)` + `WebTestClient` — a real server

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class JwtAuthenticationFilterTest {
    @Autowired private WebTestClient webTestClient;
```

Boots on a real port and makes real HTTP calls.

`api-gateway` needs this because it is **reactive** (Spring WebFlux, not Spring
MVC). `MockMvc` does not exist in that world — the reactive equivalent is
`WebTestClient`.

---

## 3. Supporting concepts

### `@MockBean` vs `@Mock`

| | What it does |
|---|---|
| `@Mock` | A plain Mockito fake. You wire it in yourself. No Spring. |
| `@MockBean` | Replaces that bean **inside the Spring context**. Everything that depends on it now gets the fake. |

Use `@Mock` in unit tests, `@MockBean` when Spring is running.

### Test profiles — and the trap we fell into

The clean way to override config for tests:

```
src/main/resources/application.yml        ← production, never touched
src/test/resources/application-test.yml   ← test overrides
```

plus `@ActiveProfiles("test")` on the test class. Spring loads **both**, with
the profile file winning on conflicts.

**The trap:** our first attempt named the test file `application.yml`. Both
files then had the same classpath name, and the test one *shadowed* the main
one entirely — so the gateway lost every route it had. Tests expecting `401`
suddenly got `404`, because with no route matched, the JWT filter never even
ran.

The lesson: `application.yml` in test resources **replaces** production config.
`application-test.yml` **layers on top of it**. Almost always you want the
second.

### H2 for the database

`@SpringBootTest` on a JPA service needs *a* DataSource or the context won't
start. Options were:

1. Real Postgres — needs infrastructure running for every `mvn test`
2. Testcontainers — needs Docker, and is a large new dependency
3. **H2 in-memory, test scope only** ← what we chose

H2 works here because our entities use no Postgres-specific column types. We
set `ddl-auto: none` so Hibernate never tries to create or validate a schema —
we are testing *wiring*, not SQL. Production still runs Postgres with Flyway
owning the schema, completely unchanged.

---

## 4. What we actually built

### Current state — 61 tests, all passing

| Module | Tests | Who wrote them | Style |
|---|---|---|---|
| `shared-contracts` | 7 | pre-existing | plain JUnit |
| **`api-gateway`** | **7** | **added here** | `@SpringBootTest` + `WebTestClient` |
| **`auth-service`** | **9** | **added here** | `@SpringBootTest` + `MockMvc` |
| `integration-service` | 23 | pre-existing | Mockito + `@WebMvcTest` |
| `metrics-service` | 0 | — | (service is still an empty shell) |
| `notification-service` | 15 | pre-existing | Mockito + `@WebMvcTest` |

We added tests to **api-gateway and auth-service only**. Tests briefly added to
the other three were removed at your request and those services are now
byte-identical to `HEAD`.

### api-gateway — 7 tests

One context test, plus six on the JWT edge filter:

| Case | What it proves |
|---|---|
| missing token | filter rejects unauthenticated traffic |
| malformed token | garbage is not accepted |
| wrong scheme (`Basic`) | only `Bearer` is honoured |
| **expired token** | `exp` is actually enforced |
| **valid token** | a genuine token gets *through* |
| JSON error shape | 401 carries `{status, error, path}` |

Two details worth understanding:

**Why `/api/alerts/**`?** Gateway global filters only run for **matched
routes**. Testing an unrouted path would return 404 before authentication was
ever considered — proving nothing.

**Why does the valid-token test assert "not 401" instead of "200"?** Once the
filter admits the request, routing forwards it to notification-service, which
is not running. So the response is a downstream failure. The subject under test
is the filter's *admit/reject decision*, and "not 401" is exactly that. Claiming
200 would be testing something we did not start.

### auth-service — 9 tests

One context test, plus eight endpoint tests covering the full contract:

```
POST /auth/register  → 201 success · 400 bad email · 400 short password · 409 duplicate
POST /auth/login     → 200 success · 401 bad credentials
GET  /auth/me        → rejected without auth · 200 with authenticated principal
```

Note the last one: the controller reads `@AuthenticationPrincipal User`, so the
test principal has to be the **real `User` entity** (it implements
`UserDetails`). A generic `@WithMockUser` would inject the wrong principal type
and the controller would receive `null`.

---

## 5. The principle we kept returning to

> **Never weaken production code to make a test pass.**

Every test here runs against the real `SecurityConfig`, the real JWT filter, the
real validation annotations. We mocked *collaborators* (`AuthService`) and
swapped *infrastructure* (Postgres → H2, test-scope only). We never disabled a
security rule, relaxed a constraint, or changed a controller to be easier to
test.

A test that passes because you weakened the thing it tests is worse than no
test — it reports safety you do not have.

---

## 6. What is still untested (honest gaps)

- **`metrics-service`** — one class, nothing to test yet
- **Repository / SQL layer** — no `@DataJpaTest` anywhere; queries are unverified
  against a real schema
- **RabbitMQ end-to-end** — publishers and listeners are unit-tested with mocks;
  no test sends a real message through a real broker
- **`analytics-service`** — the Python ML service has no tests at all. It cannot
  use `@SpringBootTest`; the equivalent there is `pytest`
- **Gateway rate limiting** — the Redis-backed limiter is not exercised

None of these are blocking. They are the honest next steps.

---

## 7. Running things

```bash
cd backend

mvn test                                  # all 61
mvn verify                                # compile + test + package
mvn -pl auth-service -am test             # one service
mvn -pl auth-service test -Dtest=AuthControllerTest
mvn -pl auth-service test -Dtest=AuthControllerTest#loginReturns200AndToken

# Just the context tests across every module
mvn test -Dtest='*ApplicationTests' -Dsurefire.failIfNoSpecifiedTests=false
```

That last flag needs the `surefire.` prefix. Without it Maven fails on
`shared-contracts`, which has no class matching the pattern.

**No infrastructure is required.** No Postgres, no RabbitMQ, no Redis, no
Docker. That is a deliberate property: a test suite that needs a running
environment is a test suite people stop running.
