package com.pokernight.table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokernight.game.GameVariant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class TableService {
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  public TableService(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }

  public Map<String,Object> create(String hostId, String name) {
    String id = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Map<String,String> table = new HashMap<>(Map.of("tableId", id, "name", name, "hostId", hostId, "status", "WAITING_FOR_PLAYERS", "boot", "5", "bank", "1000", "stateVersion", "0", "createdAt", Instant.now().toString()));
    redis.opsForHash().putAll(key(id), table);
    join(id, hostId, hostId, 0);
    return snapshot(id);
  }

  public Map<String,Object> join(String tableId, String playerId, String displayName, int seat) {
    if (!redis.hasKey(key(tableId))) throw new IllegalArgumentException("TABLE_NOT_FOUND");
    if (seat < 0 || seat > 7) throw new IllegalArgumentException("SEAT_INVALID");
    redis.opsForHash().put(seatKey(tableId, seat), "playerId", playerId);
    redis.opsForHash().put(seatKey(tableId, seat), "displayName", displayName);
    redis.opsForHash().put(seatKey(tableId, seat), "balance", "100");
    redis.opsForHash().put(seatKey(tableId, seat), "connection", "CONNECTED");
    redis.opsForHash().put(seatKey(tableId, seat), "participation", "NEXT_HAND");
    return snapshot(tableId);
  }

  public Map<String,Object> snapshot(String tableId) {
    Map<Object,Object> table = redis.opsForHash().entries(key(tableId));
    List<Map<String,Object>> seats = new ArrayList<>();
    for (int i=0;i<8;i++) { Map<Object,Object> seat = redis.opsForHash().entries(seatKey(tableId,i)); if (!seat.isEmpty()) { Map<String,Object> safe = new LinkedHashMap<>(); safe.put("seat",i); safe.putAll(seat.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), e -> e.getValue()))); seats.add(safe); } }
    return new LinkedHashMap<>(Map.of("table", table.entrySet().stream().collect(java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), e -> e.getValue())), "seats", seats, "publicCards", List.of()));
  }

  public Map<String,Object> apply(String tableId, String playerId, Map<String,Object> action) {
    String type = String.valueOf(action.getOrDefault("type", "UNKNOWN"));
    String actionId = String.valueOf(action.getOrDefault("actionId", UUID.randomUUID()));
    String ledger = "table:" + tableId + ":actions";
    if (Boolean.TRUE.equals(redis.opsForSet().isMember("table:" + tableId + ":accepted_actions", actionId))) return snapshot(tableId);
    String version = String.valueOf(action.getOrDefault("stateVersion", "0"));
    String current = String.valueOf(redis.opsForHash().get(key(tableId), "stateVersion"));
    if (!version.equals(current)) throw new IllegalStateException("STALE_STATE_REJECTED");
    redis.opsForSet().add("table:" + tableId + ":accepted_actions", actionId);
    redis.opsForHash().increment(key(tableId), "stateVersion", 1);
    Map<String,String> event = Map.of("actionId", actionId, "actor", playerId, "type", type, "at", Instant.now().toString());
    try { redis.opsForStream().add(ledger, event); } catch (Exception ignored) { /* Redis 6 compatibility fallback: state remains authoritative */ }
    return snapshot(tableId);
  }

  private String key(String id) { return "table:" + id; }
  private String seatKey(String id, int seat) { return "table:" + id + ":seat:" + seat; }
}
