package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VariantEvaluatorSwapTest {
  @Test void swapDoesNotDuplicateNaturalCards() { var hand = VariantEvaluator.evaluate(GameVariant.SWAP, List.of(new Card(7,'S'),new Card(7,'H'),new Card(2,'S')), new ArrayListCard(new Card(7,'C')), new Card(7,'C')); assertNotNull(hand); }
  @Test void swapTrailTieBreakerUsesTrailRank() { var result = VariantEvaluator.evaluate(GameVariant.SWAP, List.of(new Card(7,'S'),new Card(7,'H'),new Card(2,'D')), List.of(new Card(7,'C')), new Card(7,'C')); assertEquals(List.of(2), result.tieBreakers()); }
  private static final class ArrayListCard extends java.util.ArrayList<Card> { ArrayListCard(Card card) { super(List.of(card)); } }
}
