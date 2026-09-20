package com.pokernight.game;

public record Card(int rank, char suit) implements Comparable<Card> {
  public Card { if (rank < 2 || rank > 14) throw new IllegalArgumentException("rank 2..14"); }
  @Override public int compareTo(Card other) { return Integer.compare(rank, other.rank); }
  public String label() { return (rank == 14 ? "A" : rank == 13 ? "K" : rank == 12 ? "Q" : rank == 11 ? "J" : String.valueOf(rank)) + suit; }
}
