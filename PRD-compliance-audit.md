# PRD Compliance Audit

## Implementation update (2026-09-21)

Since initial audit, implementation added Redis JSON table state, Lua-guarded action commits, Redis-backed invite sessions, serialized join allocation, server-side deadlines and expiry sweeper, disconnect state, idle-table reclamation, classic hand lifecycle, boots, betting, show, sideshow, forced showdown, tie/odd-chip settlement, bank allocation, transfers, variant evaluators, Exchange/Fold board replacement, showdown reveal override, private-card redaction, crash refunds, CI, and Playwright browser smoke coverage. Live smoke test against supplied Redis verified table creation, second-player hand start, and redaction; browser smoke verified host-to-live-table flow and WebSocket connection.

Remaining gaps are listed below; this file is intentionally not a claim of full PRD completion.

Audit target: current workspace implementation versus `PRD-updated.md`.

Status meanings:

- **Implemented**: evidence exists and matches the PRD requirement.
- **Partial**: some structure exists, but authoritative behavior is incomplete.
- **Missing**: no implementation evidence.
- **Contradiction**: current behavior conflicts with an authoritative PRD rule.

## Summary

The current code is a playable prototype slice with authoritative Redis state, realtime table flow, settlement, recovery, and browser smoke coverage. Strict PRD completion still depends mainly on production identity integration and exhaustive acceptance/restart testing.

## Requirement matrix

| PRD area | Status | Evidence / gap |
|---|---|---|
| Angular + Ionic frontend | Implemented | `frontend/package.json`, `frontend/src/` |
| Java 25 backend | Implemented | `backend/pom.xml`, Java 25 build passed |
| Redis as live-state store | Implemented | `TableService` stores serialized authoritative table/hand state in Redis |
| Spring Data JPA | Not required | PRD makes Redis authoritative; no relational persistence is needed for prototype |
| Table capacity 2–8 | Implemented | Backend enforces 8 seats; UI renders all seats and server filters disconnected/sitting-out players |
| Invite-only access | Partial | Random table code plus Redis-backed session token/membership; no external invite lifecycle or revocation UI |
| Authenticated WebSocket identity | Partial | Redis session token binds actions to a WebSocket `Principal` and rejects claimed-ID mismatch; production JWT/OIDC remains required |
| Table/hand state machine | Implemented | `TableState`/`HandState` track lifecycle, dealer, turn, deadline, rounds, and settlement |
| Jack Deal | Partial | Cryptographic deck/Jacks establish first dealer; disconnected-seat invalidation and exhaustion reshuffle are covered by `JackDealerTest`; reconnect-observer semantics still need an acceptance test |
| Dealer rotation | Partial | Winner becomes dealer, tie ordering and 30-second variant fallback exist; full multi-hand acceptance coverage remains |
| Six game variants | Partial | `VariantEvaluator` covers all six evaluator paths; full variant flow still needs acceptance tests |
| Hand rankings | Partial | Evaluator covers categories plus all six variant paths and deterministic tie-break tests; full game-flow coverage remains |
| A-2-3 / 3-2-A semantics | Contradiction risk | Evaluator uses input/deal order to distinguish hands; physical card evaluation should be order-independent unless PRD defines a canonical representation |
| Boot amount `B=5` | Implemented | Hand start deducts boot from eligible players |
| Initial bank `1000` | Partial | Initial allocations decrement bank; create/join serialization remains to harden |
| Negative balance floor `-1000` | Partial | Funding/transfer paths enforce floor; betting currently requires available balance |
| Betting / blind / seen rules | Partial | Contributions, current chaal, raises, blind turns, and rounds exist; legal edge coverage remains |
| Max chaal `30` | Implemented | Backend caps current chaal at `30` |
| Max betting rounds `10` | Implemented | Backend triggers forced showdown at ten completed rounds |
| Insufficient-balance flow | Partial | Bank funding and `FUND_AND_WAGER` are server-side atomic transitions; player transfer uses server-tracked request/consent/refusal and consent can atomically fund the requester’s active wager; exhaustive edge coverage remains |
| Show | Implemented | Server validates two-player state, first-action restriction, fee, and caller-loses tie |
| Sideshow | Implemented | Request/response timer, private comparison, fee, and requester-loses tie exist |
| Forced showdown | Partial | Server trigger/evaluation/payout exists; full reveal payload tests remain |
| Single main pot | Implemented | Contributions and one-pot settlement exist |
| Ties / odd-chip clockwise payout | Implemented | Settlement divides pot and allocates remainder clockwise |
| End-of-hand reveal rules | Partial | Private-card redaction and reveal state exist; completed public reveal contract needs tests |
| 30-second server timer | Implemented | Server deadline plus scheduled expiry fold; UI derives countdown from deadline |
| Disconnect / auto-fold / sitting out | Partial | Disconnect state and expiry fold exist; all disconnected-winner cases need tests |
| Reconnect / full sync | Implemented | Redis session survives reload, socket reconnects, and stale state requests full redacted sync |
| Redis action atomicity | Implemented | Lua commit guards version/idempotency and writes state/action/ledger streams together |
| Duplicate action IDs | Implemented | Lua `SISMEMBER`/`SADD` guard is atomic |
| Action ledger | Partial | Redis Stream append exists with action IDs, actors, types, and versions; exceptions are swallowed and payload is incomplete |
| Chip ledger | Partial | Typed ledger stream exists with corrected per-action amounts; complete transaction taxonomy still needs coverage |
| Settlement markers | Implemented | Settlement/crash-refund markers are stored in Redis sets |
| Crash recovery | Partial | Startup scan, idempotent crash refunds, and unit coverage exist; Redis restart/replay acceptance remains |
| Redis persistence config | Partial | AOF/RDB/no-eviction example, Dockerfiles, nginx API/WebSocket proxy, Compose healthcheck, and operational guidance exist; local Docker image and container smoke now pass, while provider wiring/restore drill remain deployment-specific |
| Private-card security | Partial | Server stores cards and redacts opponent cards; leak tests remain |
| Rate/message limits | Partial | WebSocket payload limit exists; rate limiting remains |
| Accessibility announcements | Partial | `aria-live` status exists; no hand-category/turn event announcements or timer semantics |
| Browser/reconnect/crash tests | Partial | Playwright host/live-table/reload smoke passes locally and through the Docker nginx proxy; Redis restart and spoofing tests remain |
| CI | Implemented | `.github/workflows/ci.yml` builds/tests backend, builds frontend, runs browser smoke, and builds both container images |

## Direct non-compliance findings

1. WebSocket identity uses Redis-backed prototype sessions; production JWT/OIDC remains required.
2. Create/join allocation is serialized with a Redis lock, but not yet a single allocation Lua transaction.
3. Full acceptance coverage is missing for authorization spoofing, reconnect races, crash replay, browser flows, and Redis restart.
4. Jack Deal, deterministic dealer selection, and complete disconnected-winner behavior remain incomplete.

## PRD-aligned implementation choices

- Redis is used instead of JPA because the PRD explicitly makes Redis authoritative.
- Frontend/backend share one repository with separate `frontend/` and `backend/` directories.
- Java 25, Spring Boot, Angular, Ionic, and WebSocket choices match the PRD direction.
- Secrets are environment-driven through `REDIS_URL`; the Redis credential is not committed.

## Verification performed

- `mvn test`: passed; evaluator, Jack Deal, deck, variant, and crash-recovery tests pass.
- `npm run build`: passed.
- Static source audit performed with `rg` across backend and frontend.
- Local Docker build completed for both images; Compose backend reported healthy, nginx served the SPA, and containerized Playwright smoke passed through `http://localhost:8088`.

## Verdict

Not all changes are proven PRD-compliant. Current implementation is directionally aligned but materially incomplete; remaining risk centers on production identity, exhaustive acceptance/restart coverage, and provider-specific operations.
