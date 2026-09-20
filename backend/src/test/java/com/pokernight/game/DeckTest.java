package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class DeckTest {
  @Test void deckContainsFiftyTwoDistinctCards() { Deck deck = new Deck(); assertEquals(52, new HashSet<>(deck.draw(52)).size()); assertThrows(IllegalStateException.class, deck::draw); }
}
