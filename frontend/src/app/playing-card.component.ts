import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PokerCard, rankLabel, suitGlyph, suitLabel } from './card.types';

@Component({
  selector: 'pn-playing-card',
  standalone: true,
  templateUrl: './playing-card.component.html',
  styleUrl: './playing-card.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlayingCardComponent {
  card = input<PokerCard | null>(null);
  faceUp = input(true);
  size = input<'sm' | 'md' | 'lg'>('md');
  emphasized = input(false);

  rank = computed(() => rankLabel(this.card()?.rank ?? 0));
  suit = computed(() => suitGlyph(this.card()?.suit ?? ''));
  suitName = computed(() => suitLabel(this.card()?.suit ?? ''));
  isRed = computed(() => ['D', 'H'].includes(this.card()?.suit ?? ''));
  isJoker = computed(() => (this.card()?.rank ?? 0) === 15);
  ariaLabel = computed(() => this.faceUp() && this.card() ? `${this.rank()} of ${this.suitName()}` : 'Hidden playing card');
}
