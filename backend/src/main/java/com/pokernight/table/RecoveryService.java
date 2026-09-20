package com.pokernight.table;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecoveryService implements CommandLineRunner {
  private final StringRedisTemplate redis; private final TableStateCodec codec;
  public RecoveryService(StringRedisTemplate redis, TableStateCodec codec) { this.redis=redis; this.codec=codec; }
  @Override public void run(String... args) { Set<String> keys=redis.keys("table:*:state"); if(keys==null) return; for(String key:keys) recover(key); }
  private void recover(String key) { try { TableState state=codec.read(redis.opsForValue().get(key)); if(state.hand==null || !"IN_PROGRESS".equals(state.hand.status)) return; String marker="table:"+state.tableId+":settlement:CRASH_REFUND:"+state.hand.handId; if(redis.opsForSet().add("table:"+state.tableId+":settlements",marker)==1) { for(SeatState seat:state.seats) { seat.balance += seat.contribution; seat.contribution=0; } state.hand.status="CANCELED"; state.status=TableStatus.WAITING_FOR_PLAYERS; state.stateVersion++; redis.opsForStream().add("table:"+state.tableId+":ledger", Map.of("type","CRASH_REFUND","hand_id",state.hand.handId)); redis.opsForValue().set(key,codec.write(state)); } } catch(Exception ignored) { } }
}
