export type CardSuit = 'C' | 'D' | 'H' | 'S' | 'X' | 'Y';

export interface PokerCard {
  rank: number;
  suit: string;
  label?: string;
}

export function rankLabel(rank: number): string {
  return ({ 11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '★' } as Record<number, string>)[rank] ?? String(rank);
}

export function suitLabel(suit: string): string {
  return ({ C: 'clubs', D: 'diamonds', H: 'hearts', S: 'spades', X: 'joker', Y: 'joker' } as Record<string, string>)[suit] ?? 'card';
}

export function suitGlyph(suit: string): string {
  return ({ C: '♣', D: '♦', H: '♥', S: '♠', X: '★', Y: '★' } as Record<string, string>)[suit] ?? '•';
}
