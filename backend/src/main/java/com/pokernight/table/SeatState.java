package com.pokernight.table;

import com.pokernight.game.Card;
import java.util.*;

public class SeatState {
  public int seat; public String playerId; public String displayName; public int balance; public int debt; public SeatStatus status = SeatStatus.ELIGIBLE; public boolean connected; public List<Card> privateCards = new ArrayList<>(); public BetStatus betStatus = BetStatus.BLIND; public int contribution; public int blindTurns; public boolean viewed;
  public SeatState() {}
  public SeatState(int seat, String playerId, String displayName, int balance) { this.seat=seat; this.playerId=playerId; this.displayName=displayName; this.balance=balance; this.connected=true; }
  public boolean active() { return betStatus != BetStatus.FOLDED && status != SeatStatus.SITTING_OUT && status != SeatStatus.DISCONNECTED; }
}
