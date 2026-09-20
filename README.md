# Poker Night

Invite-only, real-time Teen Patti prototype.

## Structure

- `frontend/` Angular + Ionic PWA
- `backend/` Java 25 + Spring Boot + Redis

## Run

1. Set `REDIS_URL` to the Redis URI supplied for this environment.
2. Start backend: `cd backend; mvn spring-boot:run`.
3. Start frontend: `cd frontend; npm install; npm start`.
4. Open `http://localhost:4200`.

### Container stack

Build the backend artifact, then run the production-shaped nginx stack:

```bash
cd backend && mvn -DskipTests package
cd ..
REDIS_URL='redis://default:<password>@<redis-host>:<port>' docker compose up --build
```

Open `http://localhost:8088`. Nginx serves the SPA and proxies `/api` plus upgraded `/ws` traffic to the backend. Redis remains external.

Browser smoke test, with backend and frontend already running: `cd frontend; npm run e2e:smoke`. Requires Python Playwright/Chromium.

The prototype binds WebSocket actions to a server-side session principal derived from the invite/player connection. Replace the development query principal with JWT/OIDC before production.

The backend stores table state in Redis keys `table:{id}:state`, action events in `:actions`, chip events in `:ledger`, and idempotency markers in `:accepted_actions`/`:settlements`. Configure managed Redis with TLS, AOF `appendfsync everysec`, RDB snapshots, backups, and eviction disabled.

Redis operational baseline: [ops/redis.conf.example](ops/redis.conf.example), [ops/README.md](ops/README.md).

## Pinned toolchain

- Java 25.0.3 LTS
- Spring Boot 4.0.1
- Node 24.16.0
- Angular 20.3.x
- Ionic 8.7.x
- Redis 7.x compatible protocol

Virtual chips only. No purchases, cash-out, or monetary value.
