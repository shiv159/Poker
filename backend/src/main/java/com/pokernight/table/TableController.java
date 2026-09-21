package com.pokernight.table;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.pokernight.ws.TableWebSocketHandler;

@RestController
@RequestMapping("/api/tables")
public class TableController {
  private final TableService tables; private final TableWebSocketHandler sockets;
  public TableController(TableService tables, TableWebSocketHandler sockets) { this.tables = tables; this.sockets = sockets; }
  public record CreateRequest(@NotBlank String hostId, @NotBlank String name) {}
  public record JoinRequest(@NotBlank String playerId, @NotBlank String displayName, int seat) {}
  @PostMapping public ResponseEntity<?> create(@RequestBody CreateRequest request) { return ResponseEntity.ok(tables.create(request.hostId(), request.name())); }
  @GetMapping("/health") public ResponseEntity<?> health() { return ResponseEntity.ok(Map.of("status", "UP")); }
  @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable String id, @RequestHeader(value = "X-Session-Token", required = false) String token) { return ResponseEntity.ok(token == null || token.isBlank() ? tables.snapshot(id) : tables.snapshotWithToken(id, token)); }
  @GetMapping("/{id}/session/{token}") public ResponseEntity<?> restore(@PathVariable String id, @PathVariable String token) { return ResponseEntity.ok(tables.restoreSession(id, token)); }
  @PostMapping("/{id}/join") public ResponseEntity<?> join(@PathVariable String id, @RequestBody JoinRequest request) { Map<String,Object> result = tables.join(id, request.playerId(), request.displayName(), request.seat()); sockets.broadcastTableUpdate(id, "PLAYER_JOINED"); return ResponseEntity.ok(result); }
}
