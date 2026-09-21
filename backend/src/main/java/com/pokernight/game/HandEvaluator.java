package com.pokernight.game;

import java.util.*;

public final class HandEvaluator {
  private HandEvaluator() {}

  public static EvaluatedHand evaluate(List<Card> cards) {
    if (cards.size() != 3) throw new IllegalArgumentException("Teen Patti hand needs 3 cards");
    List<Integer> ranks = cards.stream().map(Card::rank).sorted(Comparator.reverseOrder()).toList();
    Map<Integer, Long> counts = cards.stream().collect(java.util.stream.Collectors.groupingBy(Card::rank, java.util.stream.Collectors.counting()));
    if (counts.size() == 1) return new EvaluatedHand(HandCategory.TRAIL, List.of(ranks.get(0)));
    boolean pure = cards.stream().map(Card::suit).distinct().count() == 1;
    List<Integer> sequence = sequenceRanks(ranks);
    if (sequence != null) return new EvaluatedHand(pure ? HandCategory.PURE_SEQUENCE : HandCategory.SEQUENCE, sequence);
    if (pure) return new EvaluatedHand(HandCategory.COLOR, ranks);
    if (counts.containsValue(2L)) {
      int pair = counts.entrySet().stream().filter(e -> e.getValue() == 2).map(Map.Entry::getKey).findFirst().orElseThrow();
      int kicker = counts.entrySet().stream().filter(e -> e.getValue() == 1).map(Map.Entry::getKey).findFirst().orElseThrow();
      return new EvaluatedHand(HandCategory.PAIR, List.of(pair, kicker));
    }
    return new EvaluatedHand(HandCategory.HIGH_CARD, ranks);
  }

  private static List<Integer> sequenceRanks(List<Integer> ranks) {
    if (ranks.equals(List.of(14, 3, 2))) return List.of(13, 1);
    if (ranks.get(0) == ranks.get(1) + 1 && ranks.get(1) == ranks.get(2) + 1) return List.of(ranks.get(0), 0);
    return null;
  }

  public static EvaluatedHand bestOf(Collection<List<Card>> hands) { return hands.stream().map(HandEvaluator::evaluate).max(EvaluatedHand::compareTo).orElseThrow(); }
}
