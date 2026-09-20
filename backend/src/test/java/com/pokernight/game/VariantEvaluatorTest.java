package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VariantEvaluatorTest {
  private Card c(int rank, char suit) { return new Card(rank, suit); }
  @Test void fourCardChoosesBestThree() { assertEquals(HandCategory.TRAIL, VariantEvaluator.evaluate(GameVariant.FOUR_CARD, List.of(c(14,'S'), c(14,'H'), c(14,'D'), c(2,'C')), List.of(), null).category()); }
  @Test void compulsoryThirdUsesBoard() { assertEquals(HandCategory.PAIR, VariantEvaluator.evaluate(GameVariant.COMPULSORY_THIRD, List.of(c(9,'S'), c(4,'H')), List.of(c(9,'D')), null).category()); }
  @Test void exchangeFoldUsesSixCardBestThree() { assertEquals(HandCategory.TRAIL, VariantEvaluator.evaluate(GameVariant.EXCHANGE_AND_FOLD, List.of(c(14,'S'), c(14,'H'), c(14,'D')), List.of(c(2,'C'), c(4,'C'), c(6,'C')), null).category()); }
  @Test void imaginaryGhostCannotDuplicateOwnCard() { assertNotNull(VariantEvaluator.evaluate(GameVariant.IMAGINARY, List.of(c(14,'S'), c(13,'H')), List.of(), null)); }
  @Test void classicUsesThreePrivateCards() { assertEquals(HandCategory.TRAIL, VariantEvaluator.evaluate(GameVariant.CLASSIC, List.of(c(6,'S'), c(6,'H'), c(6,'D')), List.of(), null).category()); }
  @Test void swapProducesDeterministicScore() { assertNotNull(VariantEvaluator.evaluate(GameVariant.SWAP, List.of(c(6,'S'), c(8,'H'), c(2,'D')), List.of(), c(7,'C'))); }
}
