package com.pokernight.game;

import com.pokernight.table.SeatState;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JackDealerTest {
  @Test
  void firstJackToConnectedEligibleSeatWins() {
    SeatState first = new SeatState(0, "p1", "One", 100);
    SeatState second = new SeatState(1, "p2", "Two", 100);
    List<Card> cards = List.of(new Card(10, 'C'), new Card(11, 'H'), new Card(11, 'S'));
    AtomicInteger index = new AtomicInteger();

    assertEquals("p2", JackDealer.findDealer(List.of(first, second), () -> cards.get(index.getAndIncrement()), () -> {}));
  }

  @Test
  void firstJokerToConnectedEligibleSeatWins() {
    SeatState first = new SeatState(0, "p1", "One", 100);
    SeatState second = new SeatState(1, "p2", "Two", 100);
    List<Card> cards = List.of(new Card(10, 'C'), new Card(15, 'X'));
    AtomicInteger index = new AtomicInteger();

    assertEquals("p2", JackDealer.findDealer(List.of(first, second), () -> cards.get(index.getAndIncrement()), () -> {}));
  }

  @Test
  void jackToDisconnectedSeatIsInvalidated() {
    SeatState disconnected = new SeatState(0, "p1", "One", 100);
    disconnected.connected = false;
    SeatState connected = new SeatState(1, "p2", "Two", 100);
    List<Card> cards = List.of(new Card(11, 'C'), new Card(11, 'D'));
    AtomicInteger index = new AtomicInteger();

    assertEquals("p2", JackDealer.findDealer(List.of(disconnected, connected), () -> cards.get(index.getAndIncrement()), () -> {}));
  }

  @Test
  void exhaustedDeckIsReshuffledAndDealContinues() {
    SeatState seat = new SeatState(0, "p1", "One", 100);
    AtomicInteger draws = new AtomicInteger();
    AtomicInteger reshuffles = new AtomicInteger();

    assertEquals("p1", JackDealer.findDealer(List.of(seat), () -> {
      if (draws.getAndIncrement() == 0) throw new IllegalStateException("DECK_EXHAUSTED");
      return new Card(11, 'S');
    }, reshuffles::incrementAndGet));
    assertEquals(1, reshuffles.get());
  }
}
