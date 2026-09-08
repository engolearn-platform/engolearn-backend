# EngoLearn Backend Service

Backend service for the EngoLearn platform, built with Spring Boot and MongoDB.

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