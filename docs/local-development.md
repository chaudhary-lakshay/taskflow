# Local development

[README](../README.md) | [Architecture & UML](diagrams/architecture.md)

Requires JDK 17+ (Gradle resolves Java 21), Node 24.14.0 and Docker.

From the repository root, copy `.env.example` to `.env`. Set `POSTGRES_PASSWORD` and `JWT_SECRET` (a base64-encoded random value of at least 32 bytes, generated with `openssl rand -base64 32`).

```bash
docker compose up -d db
```

Start the backend in a separate terminal:

```bash
cd backend
export DB_PASSWORD='your-postgres-password'
export JWT_SECRET="$(openssl rand -base64 32)"
./gradlew bootRun
```

PowerShell:

```powershell
cd backend
$env:DB_PASSWORD = 'your-postgres-password'
$env:JWT_SECRET = (openssl rand -base64 32)
.\gradlew.bat bootRun
```

Gradle does not load `.env`. If you customise `POSTGRES_DB` or `POSTGRES_USER`, also export the matching `DB_URL` and `DB_USER`. Keep the same JWT secret across restarts if existing sessions should remain valid.

In another terminal, from the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` to port 8080. Backend health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

For demo data outside Compose, export `SPRING_PROFILES_ACTIVE=demo` and `DEMO_PASSWORD` in the backend terminal before starting it.

## Checks

From the repository root:

```bash
bash scripts/verify.sh
```

Or run backend and frontend checks separately:

```bash
cd backend
./gradlew test
```

On Windows use `.\gradlew.bat test`. Testcontainers needs Docker; the JaCoCo report is under `backend/build/reports/jacoco/test/html`.

From the repository root:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build
```

## Configuration

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER` | Compose database and user (default `taskflow`) |
| `POSTGRES_PASSWORD` | Compose database password |
| `JWT_SECRET` | Required backend signing key, base64, at least 32 bytes |
| `JWT_ISSUER`, `JWT_TTL_SECONDS` | Default `taskflow`, 3600 |
| `FRONTEND_PORT` | Compose UI port, default 8081 |
| `SPRING_PROFILES_ACTIVE`, `DEMO_PASSWORD` | Optional demo data |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Backend outside Compose |

The Compose frontend and PostgreSQL ports bind to loopback; the backend is internal. `.env` is gitignored. Never commit actual secrets.

See [deployment diagram](diagrams/architecture.md#14-deployment) and [testing notes](testing.md).
