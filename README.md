# EngoLearn Backend Service

Backend service for the EngoLearn platform, built with Spring Boot and MongoDB.

Read the [backend documentation index](docs/README.md) for architecture, module flows, technical contracts and runbooks. Subscription starts at its [technical entry](docs/technical/subscription/README.md); VNPay setup and troubleshooting live in the [runbook](docs/runbooks/vnpay.md).

---

## Getting Started

### 1. Environment Configuration

Copy the sample `.env.example` file to create your `.env` in the project root:

```bash
cp .env.example .env
```

### 2. Start the infrastructure (Docker Compose)

```bash
docker-compose up -d
```

### 3. Run the backend service

Linux / macOS:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Windows:
```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

## Runtime profiles

Select exactly one runtime profile: `dev` for team development or `prod` for deployment.
`application.yaml` is the common layer, not a third profile. Both use real Mongo
persistence and transactions. `dev` uses the local single-node replica set from Docker
Compose; `prod` connects to a managed replica set through `MONGODB_URI` (for example,
MongoDB Atlas), with a separate application database.

```powershell
.\mvnw.cmd verify
java -jar target/engolearn-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Production requires `MONGODB_URI`, `APP_DB` and CORS origins, and inherits secure authentication
cookies from the shared configuration. The URI supplies managed-database credentials and
overrides the local host/port/replica-set settings. Production does not use local media directories.
Development seed,
DRAFT fallback and payment reset are opt-ins. See the
[runtime configuration contract](docs/architecture/runtime-configuration.md)
for variables, module integration gaps and verification responsibilities.
CI defaults to `prod` with a test-only Mongo URI binding and Testcontainers connection overrides.
See [CI verification](docs/runbooks/ci-verification.md) for requirements, reports and test-profile limitations.

## Endpoints

**Infrastructure**

MongoDB Service: localhost:27017
Mongo Express Web UI: [http://localhost:8081](http://localhost:8081) - Credentials: `admin` / `admin123`

## Learner periodic assessment

See the [F01/F02 contract](docs/technical/assessment.md). The Windows and Atlas demo is distributed with the local handoff package. New papers use Listening-first rules version 2; stored legacy attempts retain their original snapshot.


## Placement local development

See [fresh-clone Placement setup](docs/technical/placement-local-runtime.md) for shared data, bundled Listening media, safe import and explicit `dev` startup.
