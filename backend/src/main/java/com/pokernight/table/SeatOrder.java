package com.pokernight.table;

import java.util.Comparator;
import java.util.List;

public final class SeatOrder {
  private SeatOrder() {}

  public static List<SeatState> clockwiseSeats(TableState state) {
    return state.seats.stream().sorted(Comparator.comparingInt(s -> s.seat)).toList();
  }

  public static List<SeatState> clockwiseActive(TableState state) {
    int dealer = dealerSeat(state);
    return state.seats.stream().filter(SeatState::active)
        .sorted(Comparator.comparingInt(s -> clockwiseDistance(dealer, s.seat))).toList();
  }

  public static int nextSeat(TableState state, int currentSeat) {
    return clockwiseActive(state).stream().filter(s -> s.seat != currentSeat)
        .min(Comparator.comparingInt(s -> clockwiseDistance(currentSeat, s.seat)))
        .map(s -> s.seat).orElseGet(() -> clockwiseActive(state).stream().findFirst().map(s -> s.seat).orElse(-1));
  }

  public static SeatState previousSeat(TableState state, SeatState requester) {
    List<SeatState> active = clockwiseActive(state);
    if (active.size() < 2) throw new IllegalStateException("SIDESHOW_REQUIRES_THREE_PLAYERS");
    int index = active.indexOf(requester);
    return active.get((index - 1 + active.size()) % active.size());
  }

  public static int clockwiseDistance(TableState state, int seat) { return clockwiseDistance(dealerSeat(state), seat); }

  private static int dealerSeat(TableState state) {
    return state.seats.stream().filter(s -> java.util.Objects.equals(s.playerId, state.dealerId)).map(s -> s.seat).findFirst().orElse(-1);
  }

  private static int clockwiseDistance(int from, int to) { return ((to - from - 1 + 8) % 8) + 1; }
}
