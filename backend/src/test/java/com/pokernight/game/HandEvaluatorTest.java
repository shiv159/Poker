package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
  private Card c(int rank, char suit) { return new Card(rank, suit); }
  @Test void ranksTrailAbovePureSequence() { assertTrue(HandEvaluator.evaluate(List.of(c(14,'S'),c(14,'H'),c(14,'D'))).compareTo(HandEvaluator.evaluate(List.of(c(14,'S'),c(13,'S'),c(12,'S')))) > 0); }
  @Test void aceTwoThreeIsValidLowestSequence() { assertEquals(HandCategory.SEQUENCE, HandEvaluator.evaluate(List.of(c(14,'S'),c(2,'H'),c(3,'D'))).category()); }
  @Test void threeTwoAceIsNotSequence() { assertEquals(HandCategory.HIGH_CARD, HandEvaluator.evaluate(List.of(c(3,'S'),c(2,'H'),c(14,'D'))).category()); }
  @Test void pairBeatsHighCard() { assertTrue(HandEvaluator.evaluate(List.of(c(8,'S'),c(8,'H'),c(2,'D'))).compareTo(HandEvaluator.evaluate(List.of(c(14,'S'),c(9,'H'),c(2,'D')))) > 0); }
}
