package com.pokernight.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.game.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.*;
import java.util.*;
import java.time.Duration;

@Service
public class TableService {
  private static final String COMMIT_SCRIPT = "local current=redis.call('GET',KEYS[1]); if current~=ARGV[1] then return -1 end; if redis.call('SISMEMBER',KEYS[2],ARGV[2])==1 then return 0 end; redis.call('SET',KEYS[1],ARGV[3]); redis.call('SADD',KEYS[2],ARGV[2]); redis.call('XADD',KEYS[3],'*','action_id',ARGV[2],'actor',ARGV[4],'type',ARGV[5],'state_version',ARGV[6]); if ARGV[7]~='' then redis.call('XADD',KEYS[4],'*','type',ARGV[7],'actor',ARGV[4],'amount',ARGV[8],'action_id',ARGV[2]) end; if ARGV[9]~='' then redis.call('XADD',KEYS[4],'*','type',ARGV[9],'actor',ARGV[4],'amount',ARGV[10],'action_id',ARGV[2]..':secondary') end; return 1";
  private static final String RELEASE_LOCK_SCRIPT = "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) else return 0 end";
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final DefaultRedisScript<Long> commitScript = new DefaultRedisScript<>(COMMIT_SCRIPT, Long.class);

  public TableService(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }

  public Map<String,Object> create(String hostId, String name) {
    String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    TableState state = new TableState(id, name, hostId);
    addSeat(state, hostId, hostId, 0);
    allocateInitial(state, state.seats.getFirst());
    save(state);
    return withSession(publicSnapshot(state, hostId), state.tableId, hostId);
  }

  public Map<String,Object> join(String tableId, String playerId, String displayName, int seat) {
    String lock = "table:" + tableId + ":allocation_lock"; String token = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent(lock, token, Duration.ofSeconds(5));
    if (!Boolean.TRUE.equals(acquired)) throw new IllegalStateException("TABLE_ALLOCATION_BUSY");
    try { return joinLocked(tableId, playerId, displayName, seat); } finally { redis.execute(new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class), List.of(lock), token); }
  }

  private Map<String,Object> joinLocked(String tableId, String playerId, String displayName, int seat) {
    TableState state = load(tableId);
    if (seat < 0 || seat > 7) throw new IllegalArgumentException("SEAT_INVALID");
    SeatState existing = state.seatFor(playerId);
    SeatState taken = state.seats.stream().filter(s -> s.seat == seat).findFirst().orElse(null);
    if (taken != null && !taken.playerId.equals(playerId)) throw new IllegalArgumentException("SEAT_TAKEN");
    if (existing == null) {
      if (state.seats.size() >= 8) throw new IllegalArgumentException("TABLE_FULL");
      if (state.bank < 100) throw new IllegalStateException("BANK_EXHAUSTED");
      state.bank -= 100;
      existing = addSeat(state, playerId, displayName, seat);
      existing.balance = 100;
    }
    existing.connected = true;
    existing.status = SeatStatus.ELIGIBLE;
    existing.displayName = displayName;
    state.lastActivity = Instant.now().toString();
    if (state.hand == null) {
      if (eligibleCount(state) >= 2 && state.dealerId == null) { establishDealer(state, state.seats.stream().filter(s -> s.connected && s.status == SeatStatus.ELIGIBLE).toList()); state.status = TableStatus.WAITING_FOR_DEALER; }
      else if (eligibleCount(state) < 2) state.status = TableStatus.WAITING_FOR_PLAYERS;
    }
    state.stateVersion++;
    save(state);
    return withSession(publicSnapshot(state, playerId), state.tableId, playerId);
  }

  public Map<String,Object> snapshot(String tableId, String actorId) { return publicSnapshot(load(tableId), actorId); }
  public Map<String,Object> snapshot(String tableId) { return snapshot(tableId, null); }
  public Map<String,Object> restoreSession(String tableId, String token) {
    String playerId = redis.opsForValue().get("table:" + tableId + ":session:" + token);
    if (playerId == null || playerId.isBlank()) throw new IllegalStateException("SESSION_EXPIRED");
    TableState state = load(tableId);
    SeatState seat = state.seatFor(playerId);
    if (seat == null) throw new IllegalStateException("SESSION_INVALID");
    seat.connected = true;
    seat.status = SeatStatus.ELIGIBLE;
    state.lastActivity = Instant.now().toString();
    state.stateVersion++;
    save(state);
    Map<String,Object> snapshot = publicSnapshot(state, playerId);
    snapshot.put("sessionToken", token);
    return snapshot;
  }

  public void disconnect(String tableId, String playerId) { TableState state = load(tableId); SeatState seat = state.seatFor(playerId); if (seat == null) return; seat.connected = false; seat.status = SeatStatus.DISCONNECTED; if (state.seats.stream().filter(s -> s.connected && s.status == SeatStatus.ELIGIBLE).count() < 2) state.status = TableStatus.WAITING_FOR_PLAYERS; state.stateVersion++; save(state); }

  @Scheduled(fixedDelay = 1000)
  public void expireTurns() { Set<String> keys = redis.keys("table:*:state"); if (keys == null) return; for (String key : keys) { String tableId = key.substring("table:".length(), key.length() - ":state".length()); try { TableState state = load(tableId); if (state.hand != null && "COMPLETED".equals(state.hand.status) && state.nextGameDeadline != null && Instant.now().isAfter(Instant.parse(state.nextGameDeadline)) && eligibleCount(state) >= 2) { startHand(state, GameVariant.CLASSIC); state.stateVersion++; save(state); continue; } if (state.hand == null || !"IN_PROGRESS".equals(state.hand.status) || !expired(state.hand)) continue; SeatState actor = state.seats.stream().filter(s -> s.seat == state.hand.currentSeat).findFirst().orElse(null); if (actor == null) continue; apply(tableId, actor.playerId, Map.of("actionId", "timeout:" + state.hand.handId + ":" + actor.seat, "stateVersion", state.stateVersion, "type", "FOLD")); } catch (RuntimeException ignored) { } } }

  @Scheduled(fixedDelay = 10000)
  public void markIdleTables() { Set<String> keys=redis.keys("table:*:state"); if(keys==null) return; for(String key:keys) { try { String id=key.substring("table:".length(), key.length()-":state".length()); TableState state=load(id); if(state.seats.stream().noneMatch(s -> s.connected) && Instant.parse(state.lastActivity).plusSeconds(60).isBefore(Instant.now()) && state.status != TableStatus.IDLE) { state.status=TableStatus.IDLE; state.stateVersion++; save(state); } } catch(RuntimeException ignored) { } } }

  public Map<String,Object> apply(String tableId, String actorId, Map<String,Object> action) {
    TableState state = load(tableId);
    Long rate = redis.opsForValue().increment("table:rate:" + actorId); if (rate != null && rate == 1) redis.expire("table:rate:" + actorId, Duration.ofSeconds(1)); if (rate != null && rate > 20) throw new IllegalStateException("RATE_LIMITED");
    String original = encode(state);
    String actionId = String.valueOf(action.getOrDefault("actionId", UUID.randomUUID()));
    long version = number(action.getOrDefault("stateVersion", -1));
    if (state.acceptedActions.contains(actionId)) return publicSnapshot(state, actorId);
    if (version != state.stateVersion) throw new IllegalStateException("STALE_STATE_REJECTED");
    SeatState actor = state.seatFor(actorId);
    if (actor == null) throw new IllegalStateException("NOT_A_TABLE_MEMBER");
    String type = String.valueOf(action.getOrDefault("type", "UNKNOWN"));
    int balanceBefore = actor.balance;
    int potBefore = state.hand == null ? 0 : state.hand.pot;
    applyAction(state, actor, type, action);
    state.stateVersion++;
    state.lastActivity = Instant.now().toString();
    if (commit(original, encode(state), actorId, actionId, type, state.stateVersion, ledgerType(type, state), ledgerAmount(type, state, balanceBefore, actor.balance), secondaryLedgerType(type), Math.max(0, state.hand == null ? 0 : state.hand.pot - potBefore)) == 0) return publicSnapshot(load(tableId), actorId);
    return publicSnapshot(state, actorId);
  }

  private void applyAction(TableState state, SeatState actor, String type, Map<String,Object> action) {
    if (type.equals("SET_VARIANT")) { if (!Objects.equals(state.dealerId, actor.playerId)) throw new IllegalStateException("ONLY_DEALER_SELECTS"); if (state.hand != null && !"COMPLETED".equals(state.hand.status)) throw new IllegalStateException("HAND_NOT_COMPLETE"); state.selectedVariant = GameVariant.valueOf(String.valueOf(action.getOrDefault("variant", "CLASSIC"))); return; }
    if (type.equals("SELECT_VARIANT")) { if (!Objects.equals(state.dealerId, actor.playerId)) throw new IllegalStateException("ONLY_DEALER_SELECTS"); if (state.hand != null && !"COMPLETED".equals(state.hand.status)) throw new IllegalStateException("HAND_NOT_COMPLETE"); state.selectedVariant = GameVariant.valueOf(String.valueOf(action.getOrDefault("variant", "CLASSIC"))); startHand(state, state.selectedVariant); return; }
    if (type.equals("START_NEXT_HAND")) { if (!Objects.equals(state.dealerId, actor.playerId)) throw new IllegalStateException("ONLY_DEALER_SELECTS"); if (state.hand != null && !"COMPLETED".equals(state.hand.status)) throw new IllegalStateException("HAND_NOT_COMPLETE"); startHand(state, state.selectedVariant); return; }
    if (type.equals("START_HAND")) { if (state.hand == null) startHand(state, state.selectedVariant); return; }
    if (type.equals("BANK_ALLOCATE")) { if (state.hand != null && expired(state.hand)) throw new IllegalStateException("TURN_EXPIRED"); allocateBank(state, actor); return; }
    if (type.equals("FUND_AND_WAGER")) { fundAndWager(state, actor); return; }
    if (type.equals("PLAYER_TRANSFER_REQUEST")) { requestTransfer(state, actor, String.valueOf(action.get("targetPlayerId"))); return; }
    if (type.equals("PLAYER_TRANSFER_CONSENT")) { consentTransfer(state, actor, true); return; }
    if (type.equals("PLAYER_TRANSFER_REFUSE")) { consentTransfer(state, actor, false); return; }
    if (type.equals("PLAYER_TRANSFER")) throw new IllegalStateException("TRANSFER_REQUIRES_GIVER_CONSENT");
    if (state.hand == null || !"IN_PROGRESS".equals(state.hand.status)) throw new IllegalStateException("NO_ACTIVE_HAND");
    if (expired(state.hand)) { fold(state, actor); return; }
    if (state.hand.currentSeat != actor.seat) throw new IllegalStateException("NOT_YOUR_TURN");
    switch (type) {
      case "VIEW_CARDS" -> { actor.viewed = true; actor.betStatus = BetStatus.SEEN; }
      case "CHAAL", "BLIND" -> wager(state, actor, false, 0);
      case "RAISE" -> wager(state, actor, true, (int) number(action.getOrDefault("raiseAmount", 5)));
      case "FOLD" -> fold(state, actor);
      case "SHOW" -> show(state, actor);
      case "SIDESHOW" -> sideshow(state, actor);
      case "SIDESHOW_ACCEPT", "SIDESHOW_REFUSE" -> sideshowResponse(state, actor, type.endsWith("ACCEPT"));
      default -> throw new IllegalArgumentException("ACTION_NOT_ALLOWED");
    }
  }

  private void startHand(TableState state, GameVariant variant) {
    List<SeatState> eligible = state.seats.stream().filter(s -> s.connected && s.status == SeatStatus.ELIGIBLE && s.balance >= state.boot).toList();
    if (eligible.size() < 2) throw new IllegalStateException("WAITING_FOR_PLAYERS");
    Deck deck = new Deck();
    HandState hand = new HandState();
    hand.variant = variant; state.selectedVariant = variant;
    if (state.dealerId == null) establishDealer(state, eligible);
    hand.dealerId = state.dealerId;
    hand.currentSeat = firstSeatAfterDealer(state, eligible);
    int privateCardCount = switch (variant) {
      case IMAGINARY, COMPULSORY_THIRD -> 2;
      case FOUR_CARD -> 4;
      default -> 3;
    };
    for (SeatState seat : eligible) {
      seat.privateCards = deck.draw(privateCardCount); seat.balance -= state.boot; seat.contribution = state.boot; seat.betStatus = BetStatus.BLIND; seat.viewed = false; seat.blindTurns = 0;
      hand.pot += state.boot; hand.contributions.put(seat.playerId, state.boot);
    }
    if (variant == GameVariant.SWAP || variant == GameVariant.COMPULSORY_THIRD || variant == GameVariant.EXCHANGE_AND_FOLD) hand.communityCards = deck.draw(variant == GameVariant.EXCHANGE_AND_FOLD ? 3 : 1);
    if (variant == GameVariant.SWAP) hand.referenceCard = hand.communityCards.getFirst();
    state.hand = hand; state.nextGameDeadline = null; state.status = TableStatus.IN_PROGRESS; setDeadline(hand);
  }

  private void wager(TableState state, SeatState actor, boolean raise, int raiseAmount) {
    if (raise && !List.of(5,10,15,20,25,30).contains(raiseAmount)) throw new IllegalArgumentException("RAISE_INVALID");
    if (raise) state.hand.currentChaal = Math.min(state.maxChaal, state.hand.currentChaal + raiseAmount);
    int required = (actor.betStatus == BetStatus.SEEN || actor.viewed) ? state.hand.currentChaal * 2 : state.hand.currentChaal;
    int due = Math.max(0, required - actor.contribution);
    if (actor.balance < due) throw new IllegalStateException("INSUFFICIENT_BALANCE");
    actor.balance -= due; actor.contribution += due; state.hand.pot += due; state.hand.contributions.put(actor.playerId, actor.contribution);
    if (actor.betStatus == BetStatus.BLIND) actor.blindTurns++;
    if (actor.blindTurns >= 4) { actor.betStatus = BetStatus.SEEN; actor.viewed = true; }
    advanceTurn(state);
  }
  private void fundAndWager(TableState state, SeatState actor) {
    if (state.hand == null || !"IN_PROGRESS".equals(state.hand.status)) throw new IllegalStateException("NO_ACTIVE_HAND");
    if (expired(state.hand)) throw new IllegalStateException("TURN_EXPIRED");
    if (state.hand.currentSeat != actor.seat) throw new IllegalStateException("NOT_YOUR_TURN");
    allocateBank(state, actor);
    wager(state, actor, false, 0);
  }

  private void show(TableState state, SeatState caller) {
    if (state.activeSeats().size() != 2) throw new IllegalStateException("SHOW_REQUIRES_TWO_PLAYERS");
    if (caller.contribution <= state.boot) throw new IllegalStateException("SHOW_NOT_FIRST_ACTION");
    int fee = state.hand.currentChaal * 2; if (caller.balance < fee) throw new IllegalStateException("INSUFFICIENT_BALANCE");
    caller.balance -= fee; caller.contribution += fee; state.hand.pot += fee;
    List<SeatState> active = state.activeSeats(); SeatState other = active.getFirst().playerId.equals(caller.playerId) ? active.get(1) : active.getFirst();
    EvaluatedHand a = VariantEvaluator.evaluate(state.hand.variant, caller.privateCards, state.hand.communityCards, state.hand.referenceCard);
    EvaluatedHand b = VariantEvaluator.evaluate(state.hand.variant, other.privateCards, state.hand.communityCards, state.hand.referenceCard);
    state.hand.allCardsRevealed = true; complete(state, a.compareTo(b) > 0 ? caller : other);
  }

  private void sideshow(TableState state, SeatState requester) {
    if (state.activeSeats().size() < 3) throw new IllegalStateException("SIDESHOW_REQUIRES_THREE_PLAYERS");
    if (!requester.viewed && requester.betStatus != BetStatus.SEEN) throw new IllegalStateException("SIDESHOW_REQUIRES_SEEN");
    if (requester.contribution <= state.boot) throw new IllegalStateException("SIDESHOW_NOT_FIRST_ACTION");
    if (state.hand.pendingSideshowRequesterId != null) throw new IllegalStateException("SIDESHOW_ALREADY_PENDING");
    int fee = state.hand.currentChaal * 2; if (requester.balance < fee) throw new IllegalStateException("INSUFFICIENT_BALANCE");
    SeatState responder = previousActive(state, requester); requester.balance -= fee; requester.contribution += fee; state.hand.pot += fee; state.hand.pendingSideshowRequesterId = requester.playerId; state.hand.pendingSideshowResponderId = responder.playerId; state.hand.currentSeat = responder.seat; setDeadline(state.hand);
  }

  private void sideshowResponse(TableState state, SeatState responder, boolean accept) {
    if (!Objects.equals(state.hand.pendingSideshowResponderId, responder.playerId)) throw new IllegalStateException("SIDESHOW_RESPONSE_NOT_ALLOWED");
    SeatState requester = state.seatFor(state.hand.pendingSideshowRequesterId); state.hand.pendingSideshowRequesterId = null; state.hand.pendingSideshowResponderId = null;
    if (accept) {
      EvaluatedHand requesterHand = VariantEvaluator.evaluate(state.hand.variant, requester.privateCards, state.hand.communityCards, state.hand.referenceCard); EvaluatedHand responderHand = VariantEvaluator.evaluate(state.hand.variant, responder.privateCards, state.hand.communityCards, state.hand.referenceCard);
      if (requesterHand.compareTo(responderHand) <= 0) requester.betStatus = BetStatus.FOLDED; else responder.betStatus = BetStatus.FOLDED;
    }
    if (state.activeSeats().size() <= 1) complete(state, state.activeSeats().getFirst()); else { state.hand.currentSeat = requester.seat; advanceTurn(state); }
  }

  private SeatState previousActive(TableState state, SeatState requester) { List<SeatState> active = state.activeSeats(); int index = active.indexOf(requester); return active.get((index - 1 + active.size()) % active.size()); }
  private void establishDealer(TableState state, List<SeatState> eligible) {
    DealerDeck deck = new DealerDeck();
    state.dealerId = JackDealer.findDealer(eligible, deck::draw, deck::shuffle);
  }
  private int firstSeatAfterDealer(TableState state, List<SeatState> eligible) { int dealerSeat = eligible.stream().filter(s -> Objects.equals(s.playerId, state.dealerId)).map(s -> s.seat).findFirst().orElse(eligible.getFirst().seat); return eligible.stream().filter(s -> s.seat > dealerSeat).findFirst().orElse(eligible.getFirst()).seat; }

  private void fold(TableState state, SeatState actor) { if (state.hand != null && state.hand.variant == GameVariant.EXCHANGE_AND_FOLD) state.hand.communityCards = new ArrayList<>(actor.privateCards); actor.betStatus = BetStatus.FOLDED; if (state.activeSeats().size() <= 1) complete(state, state.activeSeats().getFirst()); else advanceTurn(state); }

  private void advanceTurn(TableState state) {
    List<SeatState> active = state.activeSeats(); if (active.isEmpty()) { state.status = TableStatus.WAITING_FOR_PLAYERS; state.hand.status = "CANCELED"; return; }
    int index = 0; for (int i=0; i<active.size(); i++) if (active.get(i).seat == state.hand.currentSeat) { index = (i + 1) % active.size(); if (index == 0) state.hand.completedRounds++; break; }
    state.hand.currentSeat = active.get(index).seat;
    if (state.hand.completedRounds >= state.maxBettingRounds) forceShowdown(state); else setDeadline(state.hand);
  }

  private void forceShowdown(TableState state) {
    List<SeatState> active = state.activeSeats(); List<SeatState> winners = new ArrayList<>(); EvaluatedHand best = null;
    for (SeatState seat : active) { EvaluatedHand score = VariantEvaluator.evaluate(state.hand.variant, seat.privateCards, state.hand.communityCards, state.hand.referenceCard); if (best == null || score.compareTo(best) > 0) { best = score; winners.clear(); winners.add(seat); } else if (score.compareTo(best) == 0) winners.add(seat); }
    state.hand.allCardsRevealed = true; settle(state, winners);
  }

  private void complete(TableState state, SeatState winner) { settle(state, List.of(winner)); }
  private void settle(TableState state, List<SeatState> winners) { if (winners.isEmpty()) { state.hand.status = "CANCELED"; state.status = TableStatus.WAITING_FOR_PLAYERS; return; } int share = state.hand.pot / winners.size(); int remainder = state.hand.pot % winners.size(); List<SeatState> ordered = new ArrayList<>(winners); ordered.sort(Comparator.comparingInt(s -> clockwiseDistance(state, s.seat))); for (int i=0; i<ordered.size(); i++) ordered.get(i).balance += share + (i < remainder ? 1 : 0); state.hand.settledPot = state.hand.pot; state.hand.pot = 0; state.hand.status = "COMPLETED"; state.hand.winnerId = ordered.getFirst().playerId; state.dealerId = ordered.getFirst().playerId; state.nextGameDeadline = Instant.now().plusSeconds(30).toString(); state.status = TableStatus.WAITING_FOR_PLAYERS; for (SeatState seat : state.seats) { seat.contribution = 0; seat.status = seat.connected ? SeatStatus.ELIGIBLE : SeatStatus.SITTING_OUT; } }
  private int clockwiseDistance(TableState state, int seat) { int dealer = state.seats.stream().filter(s -> Objects.equals(s.playerId, state.dealerId)).map(s -> s.seat).findFirst().orElse(-1); return (seat - dealer + 8) % 8; }
  private void allocateBank(TableState state, SeatState actor) { if (state.bank < 100) throw new IllegalStateException("BANK_EXHAUSTED"); if (actor.balance - 100 < -1000) throw new IllegalStateException("DEBT_LIMIT_REACHED"); state.bank -= 100; actor.balance += 100; }
  private void allocateInitial(TableState state, SeatState seat) { state.bank -= 100; seat.balance = 100; }
  private void requestTransfer(TableState state, SeatState requester, String targetId) {
    if (state.pendingTransferRequesterId != null) throw new IllegalStateException("TRANSFER_ALREADY_PENDING");
    SeatState giver = state.seatFor(targetId);
    if (giver == null || giver.playerId.equals(requester.playerId) || !giver.connected || giver.balance < 100) throw new IllegalStateException("TRANSFER_NOT_ALLOWED");
    if (requester.balance - 100 < -1000) throw new IllegalStateException("DEBT_LIMIT_REACHED");
    state.pendingTransferRequesterId = requester.playerId; state.pendingTransferGiverId = giver.playerId;
  }
  private void consentTransfer(TableState state, SeatState giver, boolean accept) {
    if (!Objects.equals(state.pendingTransferGiverId, giver.playerId)) throw new IllegalStateException("TRANSFER_RESPONSE_NOT_ALLOWED");
    SeatState requester = state.seatFor(state.pendingTransferRequesterId);
    state.pendingTransferRequesterId = null; state.pendingTransferGiverId = null;
    if (!accept) return;
    if (requester == null || giver.balance < 100 || requester.balance - 100 < -1000) throw new IllegalStateException("TRANSFER_NOT_ALLOWED");
    giver.balance -= 100; requester.balance += 100; requester.debt += 100;
    if (state.hand != null && "IN_PROGRESS".equals(state.hand.status) && state.hand.currentSeat == requester.seat && !expired(state.hand)) wager(state, requester, false, 0);
  }
  private boolean expired(HandState hand) { return hand.actionDeadline != null && Instant.now().isAfter(Instant.parse(hand.actionDeadline)); }
  private void setDeadline(HandState hand) { hand.actionDeadline = Instant.now().plusSeconds(30).toString(); }
  private int eligibleCount(TableState state) { return (int) state.seats.stream().filter(s -> s.connected && s.status == SeatStatus.ELIGIBLE && s.balance >= state.boot).count(); }
  private SeatState addSeat(TableState state, String playerId, String name, int seat) { SeatState result = new SeatState(seat, playerId, name, 0); state.seats.add(result); return result; }
  private TableState load(String id) { try { String value = redis.opsForValue().get(key(id)); if (value == null) throw new IllegalArgumentException("TABLE_NOT_FOUND"); return json.readValue(value, TableState.class); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException("TABLE_STATE_UNREADABLE", e); } }
  private void save(TableState state) { redis.opsForValue().set(key(state.tableId), encode(state)); }
  private long commit(String original, String next, String actor, String actionId, String type, long version, String ledgerType, int ledgerAmount, String secondaryType, int secondaryAmount) { Long result = redis.execute(commitScript, List.of(keyFromState(next), acceptedKeyFromState(next), actionsKeyFromState(next), ledgerKeyFromState(next)), original, actionId, next, actor, type, String.valueOf(version), ledgerType, String.valueOf(ledgerAmount), secondaryType, String.valueOf(secondaryAmount)); if (result == null || result == -1) throw new IllegalStateException("STALE_STATE_REJECTED"); return result; }
  private String ledgerType(String action, TableState state) { if (state.hand != null && "COMPLETED".equals(state.hand.status)) return "POT_WIN"; return switch (action) { case "BANK_ALLOCATE", "FUND_AND_WAGER" -> "BANK_ALLOCATION"; case "PLAYER_TRANSFER", "PLAYER_TRANSFER_CONSENT" -> "PLAYER_TRANSFER"; case "SHOW" -> "SHOW_FEE"; case "SIDESHOW" -> "SIDESHOW_FEE"; case "CHAAL", "RAISE" -> "BET"; default -> ""; }; }
  private String secondaryLedgerType(String action) { return action.equals("FUND_AND_WAGER") || action.equals("PLAYER_TRANSFER_CONSENT") ? "BET" : ""; }
  private int ledgerAmount(String action, TableState state, int balanceBefore, int balanceAfter) {
    if (state.hand != null && "COMPLETED".equals(state.hand.status)) return state.hand.settledPot;
    if (action.equals("BANK_ALLOCATE") || action.equals("PLAYER_TRANSFER")) return 100;
    return Math.max(0, balanceBefore - balanceAfter);
  }
  private String keyFromState(String encoded) { try { return key(json.readTree(encoded).get("tableId").asText()); } catch (Exception e) { throw new IllegalStateException(e); } }
  private String acceptedKeyFromState(String encoded) { try { return acceptedKey(json.readTree(encoded).get("tableId").asText()); } catch (Exception e) { throw new IllegalStateException(e); } }
  private String actionsKeyFromState(String encoded) { try { return actionsKey(json.readTree(encoded).get("tableId").asText()); } catch (Exception e) { throw new IllegalStateException(e); } }
  private String ledgerKeyFromState(String encoded) { try { return ledgerKey(json.readTree(encoded).get("tableId").asText()); } catch (Exception e) { throw new IllegalStateException(e); } }
  private String encode(TableState state) { try { return json.writeValueAsString(state); } catch (Exception e) { throw new IllegalStateException("TABLE_STATE_UNWRITABLE", e); } }
  private Map<String,Object> publicSnapshot(TableState state, String actorId) { Map<String,Object> table = new LinkedHashMap<>(); table.put("tableId",state.tableId); table.put("name",state.name); table.put("hostId",state.hostId); table.put("status",state.status); table.put("boot",state.boot); table.put("bank",state.bank); table.put("stateVersion",state.stateVersion); table.put("dealerId",state.dealerId); table.put("selectedVariant",state.selectedVariant); List<Map<String,Object>> seats = new ArrayList<>(); boolean reveal = state.hand != null && state.hand.allCardsRevealed; for (SeatState seat : state.seats) { Map<String,Object> view = new LinkedHashMap<>(); view.put("seat",seat.seat); view.put("playerId",seat.playerId); view.put("displayName",seat.displayName); view.put("balance",seat.balance); view.put("debt",seat.debt); view.put("status",seat.status); view.put("connected",seat.connected); view.put("privateCardCount",seat.privateCards.size()); boolean showCards = reveal || (Objects.equals(actorId, seat.playerId) && seat.viewed); view.put("cardsHidden", !showCards && !seat.privateCards.isEmpty()); if (showCards) view.put("privateCards",seat.privateCards); seats.add(view); } Map<String,Object> response = new LinkedHashMap<>(); response.put("table",table); response.put("seats",seats); response.put("hand",state.hand); response.put("pendingTransfer", state.pendingTransferRequesterId == null ? null : Map.of("requesterId", state.pendingTransferRequesterId, "giverId", state.pendingTransferGiverId)); return response; }
  private Map<String,Object> withSession(Map<String,Object> snapshot, String tableId, String playerId) { String token = UUID.randomUUID().toString(); redis.opsForValue().set("table:" + tableId + ":session:" + token, playerId, Duration.ofHours(8)); snapshot.put("sessionToken", token); return snapshot; }
  private long number(Object value) { return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
  private String key(String id) { return "table:" + id + ":state"; } private String acceptedKey(String id) { return "table:" + id + ":accepted_actions"; } private String actionsKey(String id) { return "table:" + id + ":actions"; } private String ledgerKey(String id) { return "table:" + id + ":ledger"; }
}
