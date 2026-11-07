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
./mvnw spring-boot:run
```

Windows:
```bash
mvnw.cmd spring-boot:run
```

## Endpoints

**Infrastructure**

MongoDB Service: localhost:27017
Mongo Express Web UI: [http://localhost:8081](http://localhost:8081) - Credentials: `admin` / `admin123`

## Learner periodic assessment

See the [F01/F02 contract](docs/technical/assessment.md). The Windows and Atlas demo is distributed with the local handoff package. New papers use Listening-first rules version 2; stored legacy attempts retain their original snapshot.
