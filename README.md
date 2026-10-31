# careround-auth-service

Accounts, login and tokens for CareRound. HTTP 8081 (clients use the gateway on 8080), gRPC 9095 (activation links for
notification-service), DB `careround_auth_db`, Redis for activation tokens.

- Issues RS256 access tokens (15 min) and refresh tokens (7 days). Claims: `sub` (user id), `role`, `grade`,
  `assignedWardId`, `permissions`, `jti`.
- Admins create staff accounts (`POST /api/v1/auth/admin/create-user`); the new user gets an activation email with a
  link to a set-password page (`GET /api/v1/auth/activate-account?token=…`, form POST to the same path).
- `assignedWardId` is verified with ward-service (`WardExists`) when an account is created.

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

## Run

```bash
./mvnw clean package -DskipTests && java -jar target/careround-auth-service-0.0.1-SNAPSHOT.jar
```
