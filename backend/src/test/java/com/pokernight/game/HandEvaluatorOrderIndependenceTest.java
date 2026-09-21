package com.pokernight.game;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorOrderIndependenceTest {
  private static final List<Card> A23 = List.of(new Card(14, 'S'), new Card(2, 'H'), new Card(3, 'D'));
  @Test void allPermutationsOfAceTwoThreeAreSequences() { permutations(A23, 0).forEach(hand -> { var evaluated = HandEvaluator.evaluate(hand); assertEquals(HandCategory.SEQUENCE, evaluated.category()); assertEquals(List.of(13, 1), evaluated.tieBreakers()); }); }
  @Test void sequenceHierarchy() { var akq = HandEvaluator.evaluate(List.of(new Card(14,'S'),new Card(13,'H'),new Card(12,'D'))); var a23 = HandEvaluator.evaluate(A23); var kqj = HandEvaluator.evaluate(List.of(new Card(13,'S'),new Card(12,'H'),new Card(11,'D'))); assertTrue(akq.compareTo(a23) > 0); assertTrue(a23.compareTo(kqj) > 0); }
  private List<List<Card>> permutations(List<Card> source, int start) { if (start == source.size() - 1) return List.of(List.copyOf(source)); var result = new java.util.ArrayList<List<Card>>(); for (int i=start;i<source.size();i++) { var copy = new java.util.ArrayList<>(source); java.util.Collections.swap(copy,start,i); result.addAll(permutations(copy,start+1)); } return result; }
}
