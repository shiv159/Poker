package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
  private Card c(int rank, char suit) { return new Card(rank, suit); }
  @Test void ranksTrailAbovePureSequence() { assertTrue(HandEvaluator.evaluate(List.of(c(14,'S'),c(14,'H'),c(14,'D'))).compareTo(HandEvaluator.evaluate(List.of(c(14,'S'),c(13,'S'),c(12,'S')))) > 0); }
  @Test void aceTwoThreeIsSecondHighestSequence() { var a23 = HandEvaluator.evaluate(List.of(c(14,'S'),c(2,'H'),c(3,'D'))); assertEquals(HandCategory.SEQUENCE, a23.category()); assertEquals(List.of(13, 1), a23.tieBreakers()); assertTrue(a23.compareTo(HandEvaluator.evaluate(List.of(c(13,'S'),c(12,'H'),c(11,'D')))) > 0); }
  @Test void pairBeatsHighCard() { assertTrue(HandEvaluator.evaluate(List.of(c(8,'S'),c(8,'H'),c(2,'D'))).compareTo(HandEvaluator.evaluate(List.of(c(14,'S'),c(9,'H'),c(2,'D')))) > 0); }
  @Test void coversAllStandardCategories() {
    assertEquals(HandCategory.TRAIL, HandEvaluator.evaluate(List.of(c(7,'S'),c(7,'H'),c(7,'D'))).category());
    assertEquals(HandCategory.PURE_SEQUENCE, HandEvaluator.evaluate(List.of(c(10,'S'),c(9,'S'),c(8,'S'))).category());
    assertEquals(HandCategory.SEQUENCE, HandEvaluator.evaluate(List.of(c(10,'S'),c(9,'H'),c(8,'D'))).category());
    assertEquals(HandCategory.COLOR, HandEvaluator.evaluate(List.of(c(14,'S'),c(9,'S'),c(4,'S'))).category());
    assertEquals(HandCategory.PAIR, HandEvaluator.evaluate(List.of(c(5,'S'),c(5,'H'),c(2,'D'))).category());
    assertEquals(HandCategory.HIGH_CARD, HandEvaluator.evaluate(List.of(c(14,'S'),c(9,'H'),c(4,'D'))).category());
  }
  @Test void rankTieBreakersAreDeterministic() {
    var aceHigh = HandEvaluator.evaluate(List.of(c(14,'S'),c(9,'H'),c(4,'D')));
    var kingHigh = HandEvaluator.evaluate(List.of(c(13,'S'),c(9,'H'),c(4,'D')));
    assertTrue(aceHigh.compareTo(kingHigh) > 0);
    assertEquals(0, HandEvaluator.evaluate(List.of(c(8,'S'),c(8,'H'),c(2,'D'))).compareTo(HandEvaluator.evaluate(List.of(c(8,'C'),c(8,'D'),c(2,'H')))));
  }
}
