package com.pokernight.game;

import java.util.List;

public record EvaluatedHand(HandCategory category, List<Integer> tieBreakers) implements Comparable<EvaluatedHand> {
  @Override public int compareTo(EvaluatedHand other) {
    int result = category.compareTo(other.category);
    if (result != 0) return result;
    for (int i = 0; i < Math.max(tieBreakers.size(), other.tieBreakers.size()); i++) {
      int a = i < tieBreakers.size() ? tieBreakers.get(i) : 0;
      int b = i < other.tieBreakers.size() ? other.tieBreakers.get(i) : 0;
      if (a != b) return Integer.compare(a, b);
    }
    return 0;
  }
}
