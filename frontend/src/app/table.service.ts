import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface Seat { seat: number; playerId: string; displayName: string; balance: string; connection: string; participation: string; }
export interface Snapshot { table: Record<string,string>; seats: Seat[]; publicCards: string[]; }

@Injectable({ providedIn: 'root' })
export class TableService {
  readonly snapshot = signal<Snapshot | null>(null); readonly connected = signal(false); readonly error = signal('');
  private socket?: WebSocket;
  constructor(private http: HttpClient) {}
  async create(hostId: string, name: string): Promise<void> { const result = await firstValueFrom(this.http.post<Snapshot>('/api/tables', { hostId, name })); this.snapshot.set(result); }
  async join(tableId: string, playerId: string, displayName: string, seat: number): Promise<void> { const result = await firstValueFrom(this.http.post<Snapshot>(`/api/tables/${tableId}/join`, { playerId, displayName, seat })); this.snapshot.set(result); }
  connect(tableId: string, playerId: string): void {
    this.socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/table?tableId=${encodeURIComponent(tableId)}&playerId=${encodeURIComponent(playerId)}`);
    this.socket.onopen = () => this.connected.set(true); this.socket.onclose = () => this.connected.set(false);
    this.socket.onmessage = event => { const message = JSON.parse(event.data) as { type: string; payload?: Snapshot; code?: string }; if (message.payload) this.snapshot.set(message.payload); if (message.type === 'ERROR') this.error.set(message.code ?? 'Unknown error'); };
  }
  action(type: string, playerId: string, data: Record<string, unknown> = {}): void { const current = this.snapshot(); if (!current || !this.socket) return; this.socket.send(JSON.stringify({ tableId: current.table['tableId'], playerId, actionId: crypto.randomUUID(), stateVersion: current.table['stateVersion'], type, ...data })); }
}
