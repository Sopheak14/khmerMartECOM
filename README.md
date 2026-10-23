# KhmerMart Backend REST API

Backend REST API for KhmerMart, a comprehensive e-commerce platform built for Cambodia supporting KHQR (Bakong) payments.

## Tech Stack
- **Language & Runtime:** Java 21, Spring Boot 3.5.3, Maven Wrapper
- **Persistence:** PostgreSQL 16 (Flyway migrations, `ddl-auto: validate`), Spring Data JPA, Hibernate 6
- **Caching & Idempotency:** Redis 7 (Spring Data Redis, Spring Cache)
- **Security:** Stateless Spring Security, JWT (jjwt 0.12.6), BCrypt (strength 12), CORS with credentials
- **KHQR Integration:** Official NBC Bakong SDK (`sdk-java:1.0.0.17`) + ZXing
- **Mailing:** Mailpit (SMTP: 1025, Web UI: 8025)
- **API Documentation:** SpringDoc OpenAPI 2.x (Swagger UI at `/api/docs`, OpenAPI at `/api/v3/api-docs`)
- **Testing:** JUnit 5, AssertJ, Testcontainers (PostgreSQL 16 Alpine, Redis 7 Alpine), MockMvc

---

## Infrastructure & Ports

To prevent conflict with host services:
- **Backend API:** `http://localhost:4000/api`
- **PostgreSQL:** Port `5433` (container 5432)
- **Redis:** Port `6380` (container 6379)
- **Mailpit SMTP:** Port `1025`
- **Mailpit Web UI:** `http://localhost:8025`
- **Swagger Documentation:** `http://localhost:4000/api/docs`

---

## Quick Start

### 1. Start Infrastructure Services
```bash
docker compose up -d
```

### 2. Run Database Migrations & Start Backend
```bash
./mvnw spring-boot:run
```

### 3. Run Tests
```bash
./mvnw verify
```

---

## API Contract & Conventions
- Base API URL prefix: `/api`
- Static Uploads: `/uploads/**`
- Envelope Format:
  - Success: `{"success": true, "data": { ... }, "meta": { ... }}`
  - Error: `{"success": false, "error": {"code": "...", "message": "..."}}`
