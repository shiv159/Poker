import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { TableService } from './table.service';

@Component({ selector: 'pn-root', standalone: true, imports: [CommonModule, FormsModule, IonicModule], templateUrl: './app.component.html', styleUrl: './app.component.css' })
export class AppComponent {
  playerId = 'player-' + Math.floor(Math.random() * 900 + 100); displayName = 'Player'; tableCode = ''; tableName = 'Friday Night Table'; selectedSeat = 1; joined = signal(false); remaining = signal(30); timerClass = computed(() => this.remaining() < 10 ? 'danger' : this.remaining() < 20 ? 'warn' : 'safe');
  constructor(public game: TableService) {}
  async create(): Promise<void> { await this.game.create(this.playerId, this.tableName); this.tableCode = this.game.snapshot()?.table['tableId'] ?? ''; this.joined.set(true); this.game.connect(this.tableCode, this.playerId); }
  async join(): Promise<void> { if (!this.tableCode.trim()) return; await this.game.join(this.tableCode.trim().toUpperCase(), this.playerId, this.displayName, this.selectedSeat); this.joined.set(true); this.game.connect(this.tableCode.trim().toUpperCase(), this.playerId); }
  act(type: string): void { this.game.action(type, this.playerId); }
  seats(): number[] { return Array.from({ length: 8 }, (_, i) => i); }
  occupied(seat: number): any { return this.game.snapshot()?.seats.find(s => s.seat === seat); }
}
