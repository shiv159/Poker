package com.pokernight.table;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class RecoveryServiceTest {
  @Test
  void refundsInProgressHandOnceAndWritesMarker() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    StreamOperations<String, Object, Object> streams = mock(StreamOperations.class);
    TableStateCodec codec = new TableStateCodec(new ObjectMapper());
    when(redis.keys("table:*:state")).thenReturn(Set.of("table:T1:state"));
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    when(redis.opsForStream()).thenReturn(streams);

    TableState state = new TableState("T1", "Test", "host");
    SeatState seat = new SeatState(0, "p1", "One", 80);
    seat.contribution = 20;
    state.seats.add(seat);
    state.hand = new HandState();
    state.hand.handId = "H1";
    state.hand.status = "IN_PROGRESS";
    when(values.get("table:T1:state")).thenReturn(codec.write(state));
    when(sets.add("table:T1:settlements", "table:T1:settlement:CRASH_REFUND:H1")).thenReturn(1L);

    new RecoveryService(redis, codec).run();

    ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
    verify(values).set(eq("table:T1:state"), saved.capture());
    TableState recovered = codec.read(saved.getValue());
    assertEquals(100, recovered.seats.getFirst().balance);
    assertEquals(0, recovered.seats.getFirst().contribution);
    assertEquals("CANCELED", recovered.hand.status);
    assertEquals(TableStatus.WAITING_FOR_PLAYERS, recovered.status);
    verify(streams).add(eq("table:T1:ledger"), eq(Map.of("type", "CRASH_REFUND", "hand_id", "H1")));
  }

  @Test
  void duplicateSettlementMarkerSkipsRefund() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    SetOperations<String, String> sets = mock(SetOperations.class);
    TableStateCodec codec = new TableStateCodec(new ObjectMapper());
    when(redis.keys("table:*:state")).thenReturn(Set.of("table:T1:state"));
    when(redis.opsForValue()).thenReturn(values);
    when(redis.opsForSet()).thenReturn(sets);
    TableState state = new TableState("T1", "Test", "host");
    SeatState seat = new SeatState(0, "p1", "One", 80);
    seat.contribution = 20;
    state.seats.add(seat);
    state.hand = new HandState(); state.hand.handId = "H1"; state.hand.status = "IN_PROGRESS";
    when(values.get("table:T1:state")).thenReturn(codec.write(state));
    when(sets.add(any(String.class), any(String.class))).thenReturn(0L);

    new RecoveryService(redis, codec).run();

    assertEquals(80, seat.balance);
    assertEquals(20, seat.contribution);
    verify(values, never()).set(any(String.class), any(String.class));
  }
}
