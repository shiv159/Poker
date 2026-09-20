package com.pokernight.game;

import com.pokernight.table.SeatState;
import java.util.List;
import java.util.function.Supplier;

/** Selects the first connected eligible seat receiving a Jack or Joker. */
public final class JackDealer {
  private JackDealer() {}

  public static String findDealer(List<SeatState> seats, Supplier<Card> draw, Runnable reshuffle) {
    if (seats == null || seats.isEmpty()) throw new IllegalArgumentException("NO_ELIGIBLE_SEATS");
    int seatIndex = 0;
    int attempts = 0;
    while (attempts++ < 10000) {
      final Card card;
      try {
        card = draw.get();
      } catch (IllegalStateException exhausted) {
        reshuffle.run();
        continue;
      }
      SeatState seat = seats.get(seatIndex++ % seats.size());
      if ((card.rank() == 11 || card.rank() == 15) && seat.connected && seat.active()) return seat.playerId;
      // Cards dealt to disconnected/ineligible seats are intentionally discarded.
    }
    throw new IllegalStateException("JACK_DEAL_DID_NOT_CONVERGE");
  }
}
