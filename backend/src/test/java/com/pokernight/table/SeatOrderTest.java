package com.pokernight.table;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeatOrderTest {
  @Test void followsPhysicalSeatOrderNotJoinOrder() {
    TableState state = new TableState("T", "Test", "p2");
    state.dealerId = "p2";
    state.seats.add(new SeatState(5, "p5", "Five", 100));
    state.seats.add(new SeatState(2, "p2", "Two", 100));
    state.seats.add(new SeatState(7, "p7", "Seven", 100));
    assertEquals(7, SeatOrder.nextSeat(state, 5));
    assertEquals(2, SeatOrder.nextSeat(state, 7));
    assertEquals(5, SeatOrder.nextSeat(state, 2));
    assertEquals(5, SeatOrder.previousSeat(state, state.seats.get(2)).seat);
  }

  @Test void skipsFoldedSeats() {
    TableState state = new TableState("T", "Test", "p2");
    state.dealerId = "p2";
    var five = new SeatState(5, "p5", "Five", 100);
    var two = new SeatState(2, "p2", "Two", 100);
    var seven = new SeatState(7, "p7", "Seven", 100);
    five.betStatus = BetStatus.FOLDED;
    state.seats.addAll(java.util.List.of(five, two, seven));
    assertEquals(7, SeatOrder.nextSeat(state, 2));
  }
}
