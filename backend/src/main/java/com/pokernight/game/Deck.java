package com.pokernight.game;

import java.security.SecureRandom;
import java.util.*;

public final class Deck {
  private static final char[] SUITS = {'C', 'D', 'H', 'S'};
  private final List<Card> cards = new ArrayList<>();
  private int next;

  public Deck() { for (char suit : SUITS) for (int rank = 2; rank <= 14; rank++) cards.add(new Card(rank, suit)); shuffle(); }
  public void shuffle() { Collections.shuffle(cards, new SecureRandom()); next = 0; }
  public Card draw() { if (next >= cards.size()) throw new IllegalStateException("DECK_EXHAUSTED"); return cards.get(next++); }
  public List<Card> draw(int count) { List<Card> result = new ArrayList<>(); for (int i=0; i<count; i++) result.add(draw()); return result; }
}
