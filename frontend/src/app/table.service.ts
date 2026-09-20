import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface Seat { seat: number; playerId: string; displayName: string; balance: number; debt: number; status: string; connected: boolean; privateCards?: { rank: number; suit: string; label?: string }[]; }
export interface Snapshot { table: Record<string, any>; seats: Seat[]; hand?: { pot: number; settledPot?: number; currentChaal: number; completedRounds: number; currentSeat: number; actionDeadline?: string; status: string; variant: string; winnerId?: string; dealerId?: string; communityCards: { rank: number; suit: string; label?: string }[]; pendingSideshowRequesterId?: string; pendingSideshowResponderId?: string; }; pendingTransfer?: { requesterId: string; giverId: string } | null; sessionToken?: string; }

@Injectable({ providedIn: 'root' })
export class TableService {
  readonly snapshot = signal<Snapshot | null>(null); readonly connected = signal(false); readonly error = signal(''); readonly actionPending = signal(false);
  private socket?: WebSocket; private sessionToken = ''; private tableId = ''; private playerId = ''; private reconnectTimer?: number; private reconnectAttempts = 0;
  constructor(private http: HttpClient) {}
  async create(hostId: string, name: string): Promise<boolean> { this.error.set(''); try { const result = await firstValueFrom(this.http.post<Snapshot>('/api/tables', { hostId, name })); this.sessionToken = result.sessionToken ?? ''; this.snapshot.set(result); return true; } catch (error) { this.showHttpError(error); return false; } }
  async join(tableId: string, playerId: string, displayName: string, seat: number): Promise<boolean> { this.error.set(''); try { const result = await firstValueFrom(this.http.post<Snapshot>(`/api/tables/${tableId}/join`, { playerId, displayName, seat })); this.sessionToken = result.sessionToken ?? ''; this.snapshot.set(result); return true; } catch (error) { this.showHttpError(error); return false; } }
  async restoreSession(tableId: string, playerId: string, sessionToken: string): Promise<boolean> { this.error.set(''); try { const result = await firstValueFrom(this.http.get<Snapshot>(`/api/tables/${tableId}/session/${sessionToken}`)); this.sessionToken = result.sessionToken ?? sessionToken; this.snapshot.set(result); this.connect(tableId, playerId); return true; } catch (error) { this.showHttpError(error); return false; } }
  connect(tableId: string, playerId: string): void {
    this.tableId = tableId; this.playerId = playerId; this.reconnectAttempts = 0; this.openSocket();
  }
  private openSocket(): void {
    if (this.reconnectTimer) window.clearTimeout(this.reconnectTimer);
    this.socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/table?tableId=${encodeURIComponent(this.tableId)}&sessionToken=${encodeURIComponent(this.sessionToken)}`);
    this.socket.onopen = () => { this.reconnectAttempts = 0; this.connected.set(true); };
    this.socket.onerror = () => { if (!this.connected()) this.error.set('TABLE_CONNECTION_FAILED'); };
    this.socket.onclose = () => { this.connected.set(false); if (this.reconnectAttempts < 5) { this.reconnectAttempts++; this.reconnectTimer = window.setTimeout(() => this.openSocket(), 1000); } else this.error.set('SESSION_EXPIRED_OR_TABLE_UNAVAILABLE'); };
    this.socket.onmessage = event => { const message = JSON.parse(event.data) as { type: string; payload?: Snapshot; code?: string }; if (message.payload) { this.snapshot.set(message.payload); this.actionPending.set(false); } if (message.type === 'ERROR') { this.actionPending.set(false); this.error.set(message.code ?? 'Unknown error'); if (message.code === 'STALE_STATE_REJECTED') this.socket?.send(JSON.stringify({ type: 'FULL_STATE_SYNC', tableId: this.tableId })); } };
  }
  action(type: string, playerId: string, data: Record<string, unknown> = {}): void { const current = this.snapshot(); if (!current || !this.socket || this.actionPending()) return; this.actionPending.set(true); this.socket.send(JSON.stringify({ tableId: current.table['tableId'], playerId, actionId: crypto.randomUUID(), stateVersion: current.table['stateVersion'], type, ...data })); }
  private showHttpError(error: unknown): void { const response = error as HttpErrorResponse; const code = response?.error?.code; this.error.set(code ?? (response?.status === 404 ? 'TABLE_NOT_FOUND' : 'REQUEST_FAILED')); }
}
