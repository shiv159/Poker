package com.pokernight.table;

import com.pokernight.game.*;
import java.time.Instant;
import java.util.*;

public class HandState {
  public String handId = UUID.randomUUID().toString(); public GameVariant variant = GameVariant.CLASSIC; public String dealerId; public String status = "IN_PROGRESS"; public int pot; public int settledPot; public int currentChaal = 5; public int completedRounds; public int currentSeat = -1; public String actionDeadline; public List<Card> communityCards = new ArrayList<>(); public Card referenceCard; public Map<String, Integer> contributions = new HashMap<>(); public String pendingSideshowRequesterId; public String pendingSideshowResponderId; public String winnerId; public boolean allCardsRevealed; public String createdAt = Instant.now().toString();
}
