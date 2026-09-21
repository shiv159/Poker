# Product Requirements Document: Custom Teen Patti Prototype

**Version:** 2.2  
**Status:** Authoritative prototype specification  
**Audience:** Product, backend, frontend, and QA teams

## 1. Product Overview

Build a free-to-use, invite-only, real-time multiplayer Teen Patti prototype for Web/PWA. A table supports 2–8 participating players.

The prototype includes all six game types, boot and betting rules, show, sideshow, forced showdown, reconnect, and Redis-backed virtual-chip settlement. Mobile packaging, native Rust integration, and horizontal scaling are deferred until the prototype is playable and tested.

The application uses virtual chips only. The nominal conversion of **1 chip = ₹5** is used solely for display and mathematical calculations.

### 1.1 Virtual economy and compliance boundary

- The application is free to use.
- Chips are virtual, non-redeemable, and have no monetary value. They may be credited between players only within the active table's borrowing rules.
- Chips cannot be purchased, sold, withdrawn, exchanged, or converted into cash or goods.
- ₹5 is a notional calculation/display unit only and must not be treated as currency, payment, or wagering value.
- The host assigns virtual chips to seated players.
- The product does not provide real-money gambling, cash-out, or monetary prizes.
- At game start, the table receives a bank balance of `1000` chips.
- Each participating player receives an initial allocation of `100` chips from the bank, reducing the bank balance by `100`.
- A player may have a negative balance, but the minimum balance is `-1000`.
- A player with a negative balance may continue playing and may start later hands.
- Outstanding debt is settled when the game ends or all players leave the table.
- Bank allocations are processed one at a time; concurrent bank requests are not permitted.
- When the bank balance is zero, no further bank allocation is possible.
- If the bank cannot satisfy a request, a player may request `100` chips from another player.
- A consented player-to-player transfer increases the requesting player's debt by `100` and increases the giving player's balance by `100`.

## 2. Platforms and Technology

- **Frontend:** Angular and Ionic for the Web/PWA prototype. Capacitor mobile packaging is deferred.
- **Backend:** Java 25 LTS with an exactly pinned Spring Boot version compatible with Java 25.
- **Realtime:** Spring WebSocket with authenticated JSON messages. STOMP and Protobuf are deferred.
- **State and persistence:** One Redis primary. Redis is the authoritative live-state store, action ledger, chip ledger, hand-history store, and settlement store.
- **Game evaluator:** Java implementation for all six game types in the prototype. Rust, native FFM integration, and frontend Wasm are deferred until the game rules stabilize.
- **Deployment:** One Spring Boot server and one Redis instance on GCP. Redis Cluster, multiple game servers, and distributed table ownership are deferred.

Pin the Java, Spring Boot, Node, Angular, Ionic, browser, and Redis versions used by the prototype. CI must build the backend and frontend and run the core game tests.

## 3. Authoritative Game Rules

### 3.1 Table and hand eligibility

- A table supports 2–8 participating players.
- A hand starts only when at least two eligible players each have at least the boot amount `B`.
- A player joining during a hand becomes a spectator and participates in the next hand.
- A player leaving during a hand immediately folds. Wagered chips remain in the hand.
- A player who folds due to timer expiry is marked `SITTING_OUT` and is skipped in future hands until they select **I'm Back**.
- **I'm Back** takes effect at the start of the next hand.
- If fewer than two connected eligible players remain, the table enters `WAITING_FOR_PLAYERS`.
- If no players are connected for 60 seconds, the table becomes `IDLE` and may be reclaimed from memory.

### 3.2 Betting model

The game uses a boot-based betting model with a maximum current chaal of `30` chips and a maximum of `10` completed betting rounds.

Definitions:

- `B`: mandatory boot amount posted before cards are dealt.
- `S`: current table chaal unit, initialized to `B`.
- `C_i`: current virtual-chip balance of player `i`.
- `P_cur`: current total pot value.
- `MAX_CHAAL`: maximum current table chaal, fixed at `30` chips.
- `MAX_BETTING_ROUNDS`: maximum completed betting rounds, fixed at `10`.
- Player status is `BLIND` or `SEEN`.

`B` is `5` chips for the initial implementation. Before cards are dealt, every participating player pays one boot of `B` chips into the main pot. `S` is then initialized to `B` and the completed betting-round count is initialized to zero.

Valid actions are `BLIND`, `CHAAL`, `RAISE`, `SHOW` when permitted, and `FOLD`. Checking is disabled.

| Player status | Chaal | Raise |
|---|---:|---:|
| Blind | `S` | current chaal + `R` |
| Seen | `2S` | current chaal + `R` |

- A raise amount `R` must be `5`, `10`, `15`, `20`, `25`, or `30` chips.
- A Blind player may pay the current chaal without viewing their cards and remains Blind.
- A player may view their cards before acting and becomes Seen. A Seen player pays twice the current chaal for a Chaal.
- A raise increases the current chaal by the selected raise amount, but `S` may not exceed `MAX_CHAAL`.
- The next Blind player owes the new current chaal `S`; the next Seen player owes `2S`.
- Every turn requires a fresh payment: Blind pays `S`; Seen pays `2S`. Prior contribution never reduces the current-turn payment.
- A betting round is complete after every active player has received one turn.
- After `MAX_BETTING_ROUNDS` completed betting rounds, betting stops and a forced showdown begins.

A player may remain Blind for a maximum of two turns. On the third turn, the backend changes the player to Seen, reveals their private cards, and charges the Seen rate of `2S`.

### 3.3 Custom insufficient-balance and borrowing rule

The server must not automatically force a short all-in.

If a player cannot afford their required wager:

1. The player may request `100` chips from the bank if at least `100` chips remain;
2. If the bank cannot satisfy the request, the player may request `100` chips from another player who explicitly consents and has sufficient available balance; or
3. The player may fold.

The allocation or transfer and wager are processed as one authoritative action. Funding is not permitted after the action timer expires. A player may not borrow beyond a balance of `-1000`.

If the player does not obtain funds, fold, or complete a legal action before the timer expires, the server auto-folds them.

The funding source, bank balance, player consent, maximum negative balance, authorization, and audit record must be validated server-side.

All bank allocations and player-to-player transfers are serialized one at a time.

### 3.4 Betting-round limit and forced showdown

The game does not use a monetary pot cap. The betting limit is based on completed betting rounds:

```text
if completed_betting_rounds >= MAX_BETTING_ROUNDS:
    stop betting
    force showdown
```

The forced showdown reveals all remaining active players' hands. The best hand wins the main pot. If the result is tied, the main pot is divided equally among the tied winners.

### 3.5 Single main pot

- The game has exactly one main pot and no side pots.
- All committed wagers, including show and sideshow costs, enter the main pot.
- Folded players' committed wagers remain in the main pot as dead money.
- The remaining active player wins the main pot when all opponents fold.
- A normal showdown evaluates all remaining active players against one another.
- If the main pot is tied, it is divided equally among the tied winners.
- Any indivisible odd chip is awarded one at a time clockwise, beginning with the first tied winner to the left of the Dealer.
- There is no unmatched-wager refund in this game mode.

### 3.6 Show and sideshow

#### Standard show

- Available only when exactly two active players remain.
- The caller pays `2 × current chaal`; this cost is added to the main pot.
- Because the maximum current chaal is `30`, the maximum show fee is `60` chips.
- The show fee does not change `S` and the show ends the hand immediately.
- The requesting player may not request a show during that player's first chaal/action of the hand.
- After completing that first action, the player may request a show whenever exactly two active players remain and all other show conditions are satisfied.
- If hands tie exactly, the caller loses.

#### Forced show

- Triggered after `10` completed betting rounds.
- All remaining active players reveal their hands.
- The best hand wins the main pot. Ties split the main pot; the caller-loses rule does not apply.

#### Sideshow

- Available only when at least three active players remain.
- The requester must be Seen.
- The requester pays `2 × current chaal`; this cost is added to the main pot.
- Because the maximum current chaal is `30`, the maximum sideshow fee is `60` chips.
- The sideshow fee does not change `S`; the request counts as the requester's turn and the response does not create a separate betting round.
- The requester may not request a sideshow during that player's first chaal/action of the hand.
- The previous active player may accept or refuse.
- If accepted, both hands are compared privately and the loser folds.
- Neither hand is revealed to the table during the hand.
- On an exact tie, the requester loses.
- If refused, the requester pays nothing and may later choose Chaal, Fold, or another Sideshow when permitted.
- If the response timer expires, the request is treated as no action; the requester may later choose Chaal, Fold, or another Sideshow when permitted.
- A sideshow request has one active pending response at a time and uses the standard action timer.

### 3.7 End-of-hand reveal override

The following rule supersedes the earlier general Show All requirement:

- If the hand ends because all opponents fold, the winning player’s cards remain hidden.
- If the hand ends through a standard showdown or forced showdown, all dealt cards, including folded players’ cards, are revealed.
- Mucking is disabled after an actual showdown.

## 4. Hand Rankings

Highest to lowest:

1. Trail/Set: three cards of the same rank. A-A-A is highest.
2. Pure Sequence: three consecutive cards of the same suit. A-K-Q is highest, A-2-3 is second highest, followed by K-Q-J through 4-3-2. 3-2-A and 2-A-K are invalid.
3. Sequence: same ordering as Pure Sequence, with mixed suits.
4. Color/Flush: same suit, ranked highest card, then second, then third.
5. Pair: pair rank, then kicker.
6. High Card: highest card, then second, then third.

There is no suit-based tie-breaking. Perfect rank ties use the contextual show, forced-show, or single-main-pot rules above.

## 5. Variants

### 5.1 Classic Teen Patti

Three private cards. Highest standard three-card hand wins.

### 5.2 Swap / Mental Wildcards

- Three private cards and one open reference community card.
- The reference card is not included in the player’s four-card hand.
- If reference rank is `R`, wildcard-eligible private-card ranks are `{R-1, R, R+1}` within ranks 2–14.
- Ace is rank 14 and does not wrap to 2.
- A wildcard can assume any rank and suit.
- The evaluated hand must contain physically distinct card tuples within that player’s hand.
- If multiple wildcard assignments have equal scores, choose transformed suit order Spades > Hearts > Diamonds > Clubs for deterministic output.

### 5.3 Imaginary Game

- Two physical private cards are dealt.
- The evaluator chooses the highest-scoring abstract Ghost Card from the full rank/suit domain.
- The Ghost Card cannot duplicate either of the player’s physical cards.
- It may duplicate an opponent’s physical card because each player’s evaluation domain is independent.
- Ghost cards are displayed as abstract cards and are not treated as physical deck cards.
- Showdown ties follow standard-show or forced-show rules.

### 5.4 Compulsory Third

- Two private cards and one open community card.
- The community card must be used.
- The hand is evaluated from exactly those three cards.
- All physical cards are dealt from one deck; physical collisions are impossible.

### 5.5 Exchange and Fold

- Three private cards and three open community cards.
- Active players evaluate the best three cards from all six cards: `C(6,3) = 20` combinations.
- No manual card exchange is permitted.
- When a player folds, the existing community cards are discarded and that player’s three private cards become the new community cards.
- Mapping is positional: private card 1 → community card 1, private card 2 → community card 2, private card 3 → community card 3.
- Folding contributes no additional chips; the player's existing committed wagers remain in the main pot.
- The board replacement and fold commit as one table-state transition.

### 5.6 Four Card

- Four private cards are dealt.
- The evaluator selects the best three of four.

## 6. Initialization and Dealer Rules

### 6.1 Jack Deal

- The server deals one face-up card sequentially per connected eligible seat, clockwise from the first seat, from a standard 52-card deck containing exactly four Jacks and no Jokers.
- Dealing stops immediately when the first Jack is dealt to an eligible connected seat.
- That seat becomes the Dealer.
- A Jack dealt to a disconnected seat is permanently invalidated and cannot establish the Dealer.
- A player reconnecting during the Jack Deal remains an observer until the Dealer is established and the actual hand begins.
- If the deck is exhausted without a valid Jack, all dealt cards are collected, the deck is cryptographically reshuffled, and dealing resumes.

### 6.2 Dealer rotation

- The hand's designated winner becomes Dealer and selects the next game type within 30 seconds.
- For a sole winner, that player is the designated winner.
- For any tied result, the server selects one designated winner deterministically by scanning tied winners clockwise from the seat left of the current Dealer.
- This same deterministic tied-winner rule applies to forced-show split pots.
- If the designated winner disconnects or does not select a game type within 30 seconds, that winner remains Dealer and Classic Teen Patti is selected automatically.
- Canceled hand: the current Dealer remains Dealer.
- First hand: Dealer is established by Jack Deal.

## 7. Timers, Disconnects, and State Machine

- Every turn has a strict 30-second server-side timer.
- Timeout causes auto-fold, then `SITTING_OUT`.
- Disconnect does not pause the timer.
- If the next player is already disconnected, their normal 30-second timer begins when their turn token is assigned.
- Exactly one seat owns the active turn token at any time.
- Timer expiry and network actions are serialized through the table action transition. The first committed transition wins; later actions are rejected with `TURN_EXPIRED` or `STALE_STATE_REJECTED`.
- If all remaining players auto-fold through the same resolved transition, the hand is canceled and committed wagers are refunded according to the ledger rules.

### 7.1 Disconnected winners

This behavior applies to every disconnected winner:

1. The win is committed to the player’s Redis virtual-chip balance.
2. The player is marked `SITTING_OUT`.
3. If at least two connected eligible players remain, the next hand may begin and the disconnected winner is skipped.
4. Otherwise, the table enters `WAITING_FOR_PLAYERS`.

## 8. Persistence, Ledger, and Crash Recovery

### 8.1 Authoritative persistence

Redis is the authoritative persistence layer for the prototype. It stores live table state in Redis Hashes and durable actions, chip transactions, and table events in Redis Streams.

Each accepted action is processed by one atomic Redis Function or Lua script. The script validates the action, updates live state, writes the action and ledger entries, records the event, and increments `state_version` as one atomic operation.

Each action has a unique `action_id`. Duplicate action IDs are ignored idempotently. Rejected actions do not modify live state or the ledger.

Redis persistence must use:

- AOF persistence with `appendfsync everysec` for the prototype;
- periodic RDB snapshots;
- backups copied to durable storage;
- eviction disabled for game and ledger keys;
- TLS, authentication, and Redis ACLs;
- monitoring for memory, command latency, AOF size, and persistence failures.

### 8.2 Redis hand completion

Within one atomic Redis Function or Lua script:

1. Validate that the hand is still `IN_PROGRESS`.
2. Mark the hand `COMPLETED`.
3. Write `POT_WIN`, `CANCELED_HAND_REFUND`, or another final ledger entry to the ledger Stream.
4. Update player balance Hashes.
5. Write a settlement marker that prevents duplicate payout or refund.
6. Append the final hand event to the table event Stream.

### 8.3 Crash recovery

- A hand is created as `IN_PROGRESS` before dealing.
- On server boot, the server finds all `IN_PROGRESS` hands in Redis.
- It replays the associated action Stream and verifies the current hand state.
- It issues one `CRASH_REFUND` for every committed wager associated with an incomplete hand.
- The hand is marked `CANCELED`.
- Recovery is idempotent through unique hand, action, and settlement markers.
- A payout cannot be replayed after a hand is marked `COMPLETED`.
- If Redis is restored from an older backup, the prototype may lose actions committed after that backup; the application must surface the affected hand as canceled and refund it when possible.

## 9. Security and Authorization

- Every WebSocket connection is authenticated.
- The server derives identity from `java.security.Principal` or authenticated JWT claims.
- Client `player_id` is untrusted and is used only for structural mapping/logging.
- A mismatch between authenticated identity and client-provided identity is logged as a security violation, the action is rejected, and the session may be terminated.
- Clients cannot authorize actions for another player.
- The server validates table membership, turn ownership, state version, action legality, balances, and wager amounts.
- Client validation is advisory only.
- Private cards must not appear in opponent payloads, logs, telemetry, crash reports, or resync responses.
- Public WebSocket messages contain redacted opponent cards. Private cards are sent only to the authenticated player’s connection.
- Use TLS, basic rate limits, message-size limits, invite authorization, and secure server-side randomness.

## 10. State Synchronization and Protocol

### 10.1 State version

Each table maintains one `state_version`. It increments for every accepted game or balance action and is the client action consistency check.

The client calculates timer display from the server-provided action deadline. Timer ticks do not need separate events.

### 10.2 Action handling

Every action includes:

- `table_id`;
- `action_id` UUID;
- client `state_version`;
- action type;
- optional action data such as a wager amount or selected game type.

The server rejects stale versions, sends a full state sync, and keeps the timer running. Duplicate committed action IDs are ignored idempotently.

### 10.3 WebSocket messages

- Client action message: includes `table_id`, `action_id`, `state_version`, action type, and optional action data.
- Server table update: redacted public state, event type, `state_version`, and action deadline.
- Server private update: authenticated player’s private cards and private results only.
- Full state sync: current redacted table state plus the authenticated player’s private state.

## 11. Data Model

Core entities:

- `users`: identity and authentication data.
- `tables`: Redis Hash containing host, status, boot, maximum chaal, maximum betting rounds, bank balance, lifecycle timestamps.
- `table_seats`: Redis Hashes containing seat, user, connection state, chip allocation, current balance, debt balance, and participation state.
- `hands`: Redis Hash containing the selected variant, dealer, status, timestamps, and final outcome.
- `hand_players`: Redis Hashes containing per-hand status, contribution, winnings, and reveal state.
- `hand_actions`: Redis Stream containing action ID, authenticated actor, requested action, state versions, result, and timestamps.
- `chip_transactions`: Redis Stream containing `BANK_ALLOCATION`, `PLAYER_TRANSFER`, `BOOT`, `BET`, `SHOW_FEE`, `SIDESHOW_FEE`, `POT_WIN`, `CANCELED_HAND_REFUND`, `CRASH_REFUND`, and `DEBT_SETTLEMENT`.
- `table_events`: Redis Stream containing ordered event sequence and replay/resync payload metadata.
- `accepted_actions`: Redis Set used to prevent duplicate action processing.
- `settlement_markers`: Redis Set or Hash used to prevent duplicate payouts and refunds.

All amount fields use integer chip units. Unique action IDs and settlement markers must prevent duplicate actions, payouts, refunds, and balance updates.

## 12. Frontend and Accessibility

- The server calculates and sends the authoritative hand result.
- Angular maintains the current table state and processes WebSocket events in order.
- If the client detects a stale `state_version`, it requests `FullStateSync`.
- A 30-second timer ring changes Green → Yellow → Red and pulses below 10 seconds.
- Hand categories and turn notifications are announced via `aria-live` regions.
- Disconnected players display a reconnecting overlay while the server timer continues.
- The interface clearly labels chips as virtual and non-redeemable.

## 13. Non-Functional Requirements

For the prototype:

- Run one game server and one Redis instance.
- Use Redis AOF persistence with `appendfsync everysec` and periodic snapshots.
- Disable eviction for game and ledger keys.
- Keep basic application logs for actions, errors, reconnects, and recovery.
- Test browser behavior, reconnects, duplicate actions, stale state, Redis restart, and private-card leakage.
- Support the current target desktop browser set. Mobile packaging and formal device support are deferred.
- No production availability, RTO, RPO, load, soak, tracing, or multi-region requirements apply yet.

## 14. Acceptance Test Requirements

The test suite must cover:

- all hand rankings and tie-breakers;
- all six game types, including variant-specific dealing and showdown;
- every legal and illegal betting action;
- top-up-or-fold behavior and timer expiry;
- maximum chaal, ten-round forced showdowns, single-main-pot payouts, ties, odd chips, bank exhaustion, and player-to-player transfers;
- show, forced show, sideshow, and reveal behavior;
- duplicate actions and stale state versions;
- timer/action races;
- reconnect and full-state synchronization;
- disconnected winners;
- Jack Deal edge cases;
- Redis crash recovery and duplicate recovery execution;
- authorization spoofing attempts;
- private-card leakage tests;
- browser and basic accessibility flows.

## 15. Delivery Phases

1. **Foundation:** Pin the prototype toolchain, create the Spring Boot app, configure one Redis instance, define JSON messages, and implement the Java evaluator for all six game types.
2. **Playable table:** Authentication, invites, table lifecycle, Jack Deal, game-type selection, boot, betting, timers, and WebSocket updates.
3. **Correctness:** All six game types, show, sideshow, ten-round forced showdown, single-main-pot payouts, Redis ledger entries, reconnect, and crash recovery.
4. **Prototype polish:** Basic accessibility, error handling, browser testing, Redis backup/restore testing, and GCP deployment.

## 16. Deferred Work

The following are intentionally outside the prototype:

- iOS and Android packaging;
- STOMP and Protobuf contracts;
- Rust native library, Java FFM integration, and frontend Wasm;
- multiple game servers, Redis Cluster, replicas, and distributed table ownership;
- formal production SLOs, multi-region deployment, advanced observability, and large-scale load testing.
