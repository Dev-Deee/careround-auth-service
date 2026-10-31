# careround-auth-service

Accounts, login and tokens for CareRound. HTTP 8081 (clients use the gateway on 8080), gRPC 9095 (activation links for
notification-service), DB `careround_auth_db`, Redis for activation tokens.

- Issues RS256 access tokens (15 min) and refresh tokens (7 days). Claims: `sub` (user id), `role`, `grade`,
  `assignedWardId`, `permissions`, `jti`.
- Admins create staff accounts (`POST /api/v1/auth/admin/create-user`); the new user gets an activation email with a
  link to a set-password page (`GET /api/v1/auth/activate-account?token=…`, form POST to the same path).
- `assignedWardId` is verified with ward-service (`WardExists`) when an account is created.

## Admin: ward reassignment

`assignedWardId` is the JWT ward claim that authorises ward-scoped access (alert replay, realtime subscriptions).

| Endpoint | Permission | |
|---|---|---|
| `PUT /api/v1/auth/admin/users/{userId}/ward` body `{"wardId": "<ward publicId>"}` | `PERM_ASSIGN_WARD` | DOCTOR/NURSE only (else 400). Ward verified with ward-service: unknown 404, unreachable 503. Unknown user 404. Returns `userId, role, email, previousWardId, assignedWardId, note` |
| `GET /api/v1/auth/admin/users/ward-problems` | `PERM_ASSIGN_WARD` | DOCTOR/NURSE accounts whose ward is `MISSING` or `UNKNOWN_WARD` (one `WardExists` per distinct ward id). Returns `{total, cap, items[]}`; at most **500** items |

- **Tokens:** access tokens issued before a reassignment keep the old `assignedWardId` until they expire (at most
  15 minutes). The next login or refresh reads the new ward.
- **Audit:** auth-service has no audit table; each change is logged at INFO as
  `AUDIT ward reassignment: userId=… role=… from=<old> to=<new> by=<admin userId>`.
- `userId` is the account id (the JWT `sub`).

## Errors

Every error uses the body `{"status", "error", "message", "timestamp"}`.

- A path no endpoint maps (e.g. a collection root with a trailing slash, or a wrong sub-path) → **404**
  `No endpoint <METHOD> <path>`. A wrong HTTP method on a mapped path → 405, unsupported content type → 415,
  malformed JSON → 400.
- Anything unexpected → 500 with a generic message; the exception is logged at ERROR with its stack trace.

## Environment variables

Copy `.env.example` to `.env` (gitignored, never committed) and fill in real values; plain environment variables work too. Names only:

| Variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | Postgres credentials |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | RSA key pair (PEM; `\|` may stand for newlines) used to sign/verify tokens |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka (outbox events such as `user.registered`) |
| `USER_SERVICE_URL` | user-service base URL |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PHONE`, `SEED_ADMIN_PASSWORD` | the super-admin created on first start |
| `ACTIVATION_BASE_URL` | base of the activation link in emails. Default `http://localhost:8081/api/v1/auth/activate-account`. Set it to the **gateway**, `http://localhost:8080/api/v1/auth/activate-account`, so users open the page through the single entry point (the page's form posts back to the same path) |

## Postman

`postman/CareRound-AuthAdmin.*`: ward reassignment and ward-problems (see `postman/README.md`).

## Run

```bash
./mvnw clean package -DskipTests && java -jar target/careround-auth-service-0.0.1-SNAPSHOT.jar
```
