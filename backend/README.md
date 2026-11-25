# HealthDekho Backend

Spring Boot 3.5.7 / Java 21 REST API for the HealthDekho insurance management system.

## Prerequisites

- JDK 21 or newer (the build targets `release 21`; JDK 17 will not compile it). On this machine the VS Code terminal defaults to the `JavaSE-27` profile. That works because the pom pins Lombok 1.18.48. Boot's default 1.18.42 fails on JDK 26+ with `ExceptionInInitializerError: ...EndPosTable`
- PostgreSQL (this machine runs PostgreSQL 18 on port **5434**)
- No Maven install needed: use the wrapper (`mvnw.cmd`)

## One-time setup

1. Create the database:

   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5434 -U postgres -c "CREATE DATABASE healthdekho;"
   ```

2. Copy `local.properties.example` to `local.properties` and fill in `DB_PASSWORD`. For AI recommendations, also set your AWS details (see below). `local.properties` is git-ignored. You can also set the same names as environment variables instead.

## Run

```powershell
.\mvnw.cmd spring-boot:run
```

On startup Flyway creates the schema (`db/migration`). In the default `dev` profile it also loads the spec's sample data (`db/seed`). The `prod` profile loads the schema only.

Sample logins (dev profile): `aarav.sharma@example.com`, `meera.iyer@example.com`, `rohan.verma@example.com`. The shared dev password is in the header comment of `src/main/resources/db/seed/V100__sample_data.sql`.

## API

Authentication is a stateless JWT. Call `POST /api/auth/login`, then send `Authorization: Bearer <accessToken>` on every other request. Tokens last 60 minutes. Errors always come back as `{status, error, message, path, fieldErrors?}`.

| Method | Path | FR | Notes |
|---|---|---|---|
| POST | `/api/auth/signup` | FR-1 | No token needed. Body `{fullName, email, password, dateOfBirth, phone?}`. 201 with the profile, then log in. 409 if the email is already registered (any letter case). 400 with field errors for invalid input or anyone under 18. Passwords need 8–72 characters including a letter and a number, and are stored as BCrypt hashes |
| POST | `/api/auth/login` | FR-1 | Body `{email, password}`. 401 `Invalid email or password.` on failure |
| GET | `/api/auth/me` | FR-1 | Logged-in customer's profile |
| GET | `/api/dashboard/policies` | FR-1 | Logged-in customer's registered policies |
| GET | `/api/dashboard/available-products` | FR-1 | Active products the customer hasn't registered. `productId` feeds Register |
| GET | `/api/policies/registration-preview?productId=&startDate=` | FR-3 | Pre-filled form with server-computed maturity date, number of installments and total premium. `eligible=false` plus a reason when the entry age doesn't fit |
| POST | `/api/policies` | FR-3 | Body `{productId, startDate?, recommendationItemId?}`. 201 with the new policy number. 422 on a business-rule failure (duplicate, age, past start date) |

| GET | `/api/policies/{policyNumber}` | FR-4 | Policy search: details plus totals paid, penalty and outstanding as of today. Another customer's policy returns 404 |
| GET | `/api/policies/{policyNumber}/installments` | FR-4 | Premium schedule: status, grace end date, the penalty if paid today, outstanding amount and `payable` |
| POST | `/api/policies/{policyNumber}/payments` | FR-4 | Body `{installmentIds[], amountPaid, paymentMethod}`. 201 with one receipt per installment |
| GET | `/api/policies/{policyNumber}/payments` | FR-4 | Payment history, in payment-date order |

| GET | `/api/policy-search?policyNumber=` / `?customerName=&page=&size=` / `?policyNumber=&dateOfBirth=` | FR-5 | Paged results `{content, page, size, totalElements, totalPages, message?}` with `remainingPremium` as of today. Any other combination of criteria returns 400 |

| POST | `/api/recommendations` | FR-2 | Body: at least one of `{dateOfBirth, insuranceGoals, preferredTermMonths, preferredPolicyType}`. 201 with ranked items. Each `recommendationItemId` feeds `POST /api/policies`. 503 if the AI is unavailable |
| GET | `/api/recommendations` | FR-2 | The customer's 10 most recent recommendation requests with their items |

FR-2 notes:
- Before the AI is called, candidates are fixed by deterministic rules: active products the customer doesn't hold and whose entry age fits. Age is calculated from the date of birth given (or the profile's) with `java.time`.
- The AI (Spring AI `ChatClient` with structured JSON output) only ranks the candidates, gives each a suitability, and writes a reason. It never sees the customer's name or email.
- What the AI returns is validated. Unknown or ineligible product codes, duplicates and invalid suitabilities are dropped. The list is capped at `healthdekho.ai.max-recommendations` and re-ranked. Premiums and terms in the response come from the database.
- If the AI fails, the response is 503 and nothing is saved. No database transaction is held open during the AI call.
- Each request and its items are saved with the model name, `model-version` and `prompt-version`.
- The model is **Claude on Amazon Bedrock**, called through Spring AI's Bedrock Converse integration. A live call needs AWS credentials with `bedrock:InvokeModel` permission and access to the model in the chosen region. The tests stub the model, so they need no AWS access.

FR-5 notes:
- Search covers **all customers' policies**, as the spec describes (name search, pagination for large result sets). Any logged-in user can use it. To limit it to staff, add a role check on `PolicySearchController`.
- Policy identifier match is exact and case-insensitive. Name match is partial and case-insensitive, needs at least 2 characters, and treats `%` and `_` literally.
- Page size defaults to 10, is capped at 50 (`healthdekho.search.max-page-size`), and the request is rejected above 100.

FR-4 rules:
- The payment date is always the server's date. The amount is applied oldest installment first, and the last one reached may be part-paid.
- Paying after the grace end date adds a 10% penalty on the premium still unpaid. It's applied once per installment and recorded on the payment row.
- A payment is refused (422) if it exceeds the outstanding amount of the selected installments, includes an installment that's already paid or selected twice, doesn't reach every selected installment, or is made on a policy that isn't active. Nothing is written when a payment is refused.
- Status as of today: `PAID`, `PENDING`, `PARTIALLY_PAID`, or `OVERDUE` (unpaid after the grace period). The policy becomes `FULLY_PAID` only when every installment is paid.
- The policy row is locked while a payment is recorded. Transaction references (`TXN-HD-000004…`) come from a serialized generator.

FR-3 rules, all calculated on the server:
- Start date defaults to today and can't be in the past. Maturity date = start + term − 1 day.
- Entry age is the customer's age on the start date and must be within the product's min/max.
- Total premium = premium × (term ÷ months per installment), using `BigDecimal` rounded to 2 decimal places.
- Installment *n* is due at start + (n−1) × period. Grace period ends 1 month after each due date (`healthdekho.payment.grace-period-months`).
- Policy numbers are `HDP-<year>-<0001…>`. A Postgres advisory lock serializes registrations, so numbers and duplicate checks can't race.

The customer is always taken from the token, never from request parameters.

## Layout

| Package | Purpose |
|---|---|
| `entity`, `entity.enums` | JPA entities (never returned from controllers) |
| `repository` | Spring Data JPA repositories |
| `dto` | Java `record` request/response types |
| `service` | Business rules, calculations, transactions |
| `controller` | REST endpoints |
| `ai` | Spring AI integration |

## Configuration

| Property / env var | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5434/healthdekho` | |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / empty | |
| `JWT_SECRET` | dev profile has a dev-only key | **Required in prod**, at least 32 characters. Startup fails without it |
| `AWS_REGION` | `us-east-1` | Bedrock region where the model is enabled |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (+ `AWS_SESSION_TOKEN`) | empty | Leave empty to use the AWS default credential chain (`aws configure` / `aws sso login` profile, or an IAM role). Until credentials work, AI calls return 503 and the rest of the app still works |
| `BEDROCK_MODEL_ID` | `anthropic.claude-opus-5-5` | Use an inference-profile ID instead if your account requires one |
| `healthdekho.payment.grace-period-months` | `1` | FR-4 grace period |
| `healthdekho.payment.late-penalty-rate` | `0.10` | FR-4 late penalty |

## Schema notes

- All business tables are created by Flyway. Hibernate runs with `ddl-auto=validate`, so entity/table mismatches fail fast at startup.
- `UNIQUE (customer_id, product_id)` on `customer_policy` enforces the duplicate-registration rule.
- `premium_payment` is append-only. A trigger rejects UPDATE and DELETE.
- The spec's sample data lists only installments 1–3 of policy `HDP-2026-0001` (240 monthly). The seed generates installments 4–240 so the schedule matches its total premium.
