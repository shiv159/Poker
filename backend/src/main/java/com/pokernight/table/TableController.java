package com.pokernight.table;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/tables")
public class TableController {
  private final TableService tables;
  public TableController(TableService tables) { this.tables = tables; }
  public record CreateRequest(@NotBlank String hostId, @NotBlank String name) {}
  public record JoinRequest(@NotBlank String playerId, @NotBlank String displayName, int seat) {}
  @PostMapping public ResponseEntity<?> create(@RequestBody CreateRequest request) { return ResponseEntity.ok(tables.create(request.hostId(), request.name())); }
  @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable String id) { return ResponseEntity.ok(tables.snapshot(id)); }
  @PostMapping("/{id}/join") public ResponseEntity<?> join(@PathVariable String id, @RequestBody JoinRequest request) { return ResponseEntity.ok(tables.join(id, request.playerId(), request.displayName(), request.seat())); }
}
