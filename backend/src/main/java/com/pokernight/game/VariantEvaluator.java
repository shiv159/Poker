package com.pokernight.game;

import java.util.*;

public final class VariantEvaluator {
  private VariantEvaluator() {}

  public static EvaluatedHand evaluate(GameVariant variant, List<Card> privateCards, List<Card> board, Card reference) {
    return switch (variant) {
      case CLASSIC -> HandEvaluator.evaluate(privateCards.subList(0, 3));
      case FOUR_CARD -> best(privateCards, 3);
      case COMPULSORY_THIRD -> HandEvaluator.evaluate(concat(privateCards.subList(0, 2), board.subList(0, 1)));
      case EXCHANGE_AND_FOLD -> best(concat(privateCards, board), 3);
      case SWAP -> evaluateSwap(privateCards, reference);
      case IMAGINARY -> evaluateImaginary(privateCards);
    };
  }

  public static List<Card> bestPhysicalCards(List<Card> cards) {
    List<List<Card>> combinations = combinations(cards, 3);
    List<Card> best = combinations.getFirst(); EvaluatedHand score = HandEvaluator.evaluate(best);
    for (List<Card> candidate : combinations) { EvaluatedHand next = HandEvaluator.evaluate(candidate); if (next.compareTo(score) > 0) { best = candidate; score = next; } }
    return best;
  }

  private static EvaluatedHand evaluateSwap(List<Card> cards, Card reference) {
    Set<Integer> wildRanks = Set.of(reference.rank() - 1, reference.rank(), reference.rank() + 1);
    List<Integer> indexes = new ArrayList<>(); for (int i=0; i<cards.size(); i++) if (wildRanks.contains(cards.get(i).rank())) indexes.add(i);
    return swapSearch(cards, indexes, 0, new ArrayList<>());
  }

  private static EvaluatedHand swapSearch(List<Card> cards, List<Integer> indexes, int at, List<Card> current) {
    if (at == cards.size()) return HandEvaluator.evaluate(current);
    Card source = cards.get(at);
    EvaluatedHand best = swapSearch(cards, indexes, at + 1, append(current, source));
    if (indexes.contains(at)) for (char suit : new char[]{'S','H','D','C'}) for (int rank=2; rank<=14; rank++) {
      Card replacement = new Card(rank, suit);
      if (current.contains(replacement)) continue;
      boolean duplicate = false;
      for (int j = at + 1; j < cards.size(); j++) if (!indexes.contains(j) && cards.get(j).equals(replacement)) { duplicate = true; break; }
      if (duplicate) continue;
      EvaluatedHand candidate = swapSearch(cards, indexes, at + 1, append(current, replacement)); if (candidate.compareTo(best) > 0) best = candidate;
    }
    return best;
  }

  private static EvaluatedHand evaluateImaginary(List<Card> physical) {
    Set<Card> blocked = Set.copyOf(physical); EvaluatedHand best = null;
    for (char suit : new char[]{'S','H','D','C'}) for (int rank=2; rank<=14; rank++) { Card ghost = new Card(rank, suit); if (blocked.contains(ghost)) continue; List<Card> hand = concat(physical, List.of(ghost)); EvaluatedHand candidate = best(hand, 3); if (best == null || candidate.compareTo(best) > 0) best = candidate; }
    return best;
  }

  private static EvaluatedHand best(List<Card> cards, int size) { EvaluatedHand best = null; for (List<Card> c : combinations(cards, size)) { EvaluatedHand next = HandEvaluator.evaluate(c); if (best == null || next.compareTo(best) > 0) best = next; } return best; }
  private static List<Card> append(List<Card> source, Card card) { List<Card> result = new ArrayList<>(source); result.add(card); return result; }
  private static List<Card> concat(List<Card> a, List<Card> b) { List<Card> result = new ArrayList<>(a); result.addAll(b); return result; }
  private static List<List<Card>> combinations(List<Card> cards, int size) { List<List<Card>> result = new ArrayList<>(); choose(cards, size, 0, new ArrayList<>(), result); return result; }
  private static void choose(List<Card> cards, int size, int start, List<Card> current, List<List<Card>> result) { if (current.size() == size) { result.add(List.copyOf(current)); return; } for (int i=start; i<cards.size(); i++) { current.add(cards.get(i)); choose(cards, size, i+1, current, result); current.removeLast(); } }
}
