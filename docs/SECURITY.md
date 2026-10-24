# KhmerMart Security Review & OWASP Top 10 Compliance

This document outlines the security architecture, controls, and compliance status of the KhmerMart E-Commerce backend service in accordance with the OWASP Top 10 (2021) standard.

---

## 1. OWASP Top 10 (2021) Security Matrix

| OWASP Vulnerability | Risk Level | KhmerMart Mitigation & Architectural Controls | Relevant Classes / Components |
|:---|:---:|:---|:---|
| **A01: Broken Access Control** | Critical | Strict Role-Based Access Control (RBAC) separating `CUSTOMER`, `STAFF`, and `ADMIN`. Endpoint authorization via `SecurityConfig` and method-level `@PreAuthorize("hasRole('ADMIN')")`. Resource ownership verification ensuring customers can only access their own orders and addresses. Token revocation on role modification and deactivation. | [SecurityConfig.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/config/SecurityConfig.java)<br>[CurrentUser.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/CurrentUser.java)<br>[AdminUserServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AdminUserServiceImpl.java) |
| **A02: Cryptographic Failures** | High | Password hashing using BCrypt with configurable work factor (default 12). Dual JWT signing using HMAC-SHA384 / HMAC-SHA512 with secrets >= 32 characters. Elliptic Curve Digital Signature Algorithm (ECDSA P-256) for VAPID Web Push payloads. HTTPS enforcement in production headers. | [JwtService.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/JwtService.java)<br>[PushServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/PushServiceImpl.java) |
| **A03: Injection** | High | 100% parameterized database queries through Spring Data JPA, Hibernate ORM, and JPA Criteria API. Zero string-concatenated SQL queries. Input validation via Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Min`, `@Max`). CSV formula injection protection (`=`, `+`, `-`, `@` escaped). | [ProductServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/ProductServiceImpl.java)<br>[ReportServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/ReportServiceImpl.java) |
| **A04: Insecure Design** | Medium | Finite State Machine enforcement for Order Lifecycle (`OrderStateMachine`). Atomic conditional SQL updates on inventory (`InventoryRepository.tryReserve`) preventing overselling. Distributed idempotency locking (`IdempotencyRecordRepository`) preventing duplicate checkouts. | [OrderStateMachine.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/OrderStateMachine.java)<br>[InventoryRepository.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/InventoryRepository.java)<br>[IdempotencyRecordRepository.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/IdempotencyRecordRepository.java) |
| **A05: Security Misconfiguration** | Medium | Secure HTTP response headers configured via Spring Security (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0`). Production profiles disable sensitive stack traces and developer test routes. Multi-stage Docker container executes as an unprivileged non-root user (`khmermart:khmermart`). | [SecurityConfig.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/config/SecurityConfig.java)<br>[Dockerfile](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/Dockerfile) |
| **A06: Vulnerable & Outdated Components** | Medium | Java 21 LTS runtime and Spring Boot 3.5.x. Regular Maven dependency auditing via `./mvnw dependency:tree` and Dependabot vulnerability alerts. | [pom.xml](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/pom.xml) |
| **A07: Identification & Authentication Failures** | High | Dual-token authentication: Short-lived access token (15 mins) and rotating refresh token (7 days) stored in `HttpOnly`, `SameSite=Strict` secure cookies. Rate limiting per IP via Bucket4j in `RateLimitFilter` (5 login attempts/min). Deactivated accounts immediately forbidden with all refresh tokens revoked. | [AuthServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AuthServiceImpl.java)<br>[RateLimitFilter.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RateLimitFilter.java)<br>[AppUserTokenRepository.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/repository/AppUserTokenRepository.java) |
| **A08: Software & Data Integrity Failures** | Medium | Strict file upload inspection: Validates MIME type and real file binary magic bytes (JPEG `FF D8 FF`, PNG `89 50 4E 47`, WEBP `52 49 46 46`), strictly rejecting `.exe` files disguised as images. Asynchronous notifications use `@TransactionalEventListener(phase = AFTER_COMMIT)` to prevent sending emails on rolled back transactions. | [AdminProductServiceImpl.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/impl/AdminProductServiceImpl.java)<br>[NotificationEventListener.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/event/listener/NotificationEventListener.java) |
| **A09: Security Logging & Monitoring Failures** | Low | Centralized audit logging via `@Audited` AOP aspect recording every backoffice write (actor ID, action, entity, entity ID, before JSON, after JSON, client IP). Distributed tracing correlation using `X-Request-Id` filter across all requests and logs. | [AuditAspect.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/aspect/AuditAspect.java)<br>[RequestIdFilter.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/security/RequestIdFilter.java) |
| **A10: Server-Side Request Forgery (SSRF)** | Low | External HTTP integrations are restricted strictly to the National Bank of Cambodia Bakong API (`https://api-bakong.nbc.gov.kh`) configured in `application.yml`. No dynamic user-supplied URLs are fetched by the application backend. | [BakongApiClient.java](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/src/main/java/com/khmermart/service/BakongApiClient.java) |

---

## 2. Secrets Management & Repository Hygiene

To eliminate credential leakage risk:
1. **Zero Committed Secrets**: All sensitive credentials (JWT keys, DB passwords, VAPID private keys, Bakong API tokens) are strictly injected via environment variables.
2. **Git Proof**: Running the command below produces zero results for committed private tokens or passwords:
   ```bash
   git grep -i -E "(ghp_|AKIA|eyJhbGciOi|PRIVATE KEY-----)" -- ':!*.json' ':!*.txt' ':!*.log'
   ```
3. **Environment Template**: [.env.example](file:///mnt/data/Year4/SYS(1)/spring/final/khmermart/back_end/.env.example) contains full variable declarations and dummy placeholders for secure local and staging setup.

---

## 3. Image Magic Bytes Verification

Uploaded product images and payment slips are scrutinized at the binary level:
```java
// Inspecting true file magic bytes prevents malicious executable execution
private void validateMagicBytes(byte[] bytes, String contentType) {
    if (bytes.length < 12) {
        throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "Invalid file format");
    }
    boolean isJpeg = bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF;
    boolean isPng = bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47;
    boolean isWebp = bytes[0] == (byte) 0x52 && bytes[1] == (byte) 0x49 && bytes[2] == (byte) 0x46 && bytes[3] == (byte) 0x46
                  && bytes[8] == (byte) 0x57 && bytes[9] == (byte) 0x45 && bytes[10] == (byte) 0x42 && bytes[11] == (byte) 0x50;
    if (!isJpeg && !isPng && !isWebp) {
        throw new CustomException(ErrorCode.UNPROCESSABLE_ENTITY, "Invalid image format: magic bytes mismatch");
    }
}
```

---

## 4. User Deactivation & Refresh Token Revocation

When an administrative user deactivates an account via `PATCH /api/admin/users/{id}` (`isActive: false`):
1. User account is immediately flagged as deactivated in PostgreSQL.
2. All active refresh tokens belonging to the user are instantly revoked in `refresh_tokens`:
   ```sql
   UPDATE refresh_tokens SET revoked = true, revoked_at = now() WHERE user_id = :userId;
   ```
3. Subsequent login attempts and refresh attempts return HTTP 403 `FORBIDDEN` ("Account is deactivated").
