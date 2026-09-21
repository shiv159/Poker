package com.pokernight.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Separate opening deal deck: first Jack establishes the dealer. */
public final class DealerDeck {
  private final List<Card> cards = new ArrayList<>();
  private int next;
  public DealerDeck() { reset(); }
  public Card draw() { if (next >= cards.size()) throw new IllegalStateException("DECK_EXHAUSTED"); return cards.get(next++); }
  public void shuffle() { Collections.shuffle(cards, new SecureRandom()); next = 0; }
  private void reset() { cards.clear(); for (char suit : new char[]{'C','D','H','S'}) for (int rank = 2; rank <= 14; rank++) cards.add(new Card(rank, suit)); shuffle(); }
}
