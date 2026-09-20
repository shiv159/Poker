package com.pokernight.table;

import com.pokernight.game.GameVariant;

import java.time.Instant;
import java.util.*;

public class TableState {
  public String tableId; public String name; public String hostId; public TableStatus status = TableStatus.WAITING_FOR_PLAYERS; public int boot = 5; public int maxChaal = 30; public int maxBettingRounds = 10; public int bank = 1000; public long stateVersion; public String dealerId; public GameVariant selectedVariant = GameVariant.CLASSIC; public String nextGameDeadline; public String pendingTransferRequesterId; public String pendingTransferGiverId; public String lastActivity = Instant.now().toString(); public HandState hand; public List<SeatState> seats = new ArrayList<>(); public Set<String> acceptedActions = new HashSet<>();
  public TableState() {}
  public TableState(String tableId, String name, String hostId) { this.tableId=tableId; this.name=name; this.hostId=hostId; }
  public SeatState seatFor(String playerId) { return seats.stream().filter(s -> Objects.equals(s.playerId, playerId)).findFirst().orElse(null); }
  public List<SeatState> activeSeats() { return seats.stream().filter(SeatState::active).toList(); }
}
