# Poker Night

Invite-only, real-time Teen Patti prototype.

## Structure

- `frontend/` Angular + Ionic PWA
- `backend/` Java 25 + Spring Boot + Redis

## Run

1. Set `REDIS_URL` to the Redis URI supplied for this environment.
2. Start backend: `cd backend; ./mvnw spring-boot:run` (Windows: `mvnw.cmd`).
3. Start frontend: `cd frontend; npm install; npm start`.
4. Open `http://localhost:4200`.

Default local identity is selected in the UI. Prototype auth uses a signed-in player ID supplied to the backend session; replace with JWT/OIDC before production.

## Pinned toolchain

- Java 25.0.3 LTS
- Spring Boot 4.0.1
- Node 24.16.0
- Angular 20.3.x
- Ionic 8.7.x
- Redis 7.x compatible protocol

Virtual chips only. No purchases, cash-out, or monetary value.
