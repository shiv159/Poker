package com.pokernight.ws;

import com.fasterxml.jackson.databind.*;
import com.pokernight.table.TableService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TableWebSocketHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(TableWebSocketHandler.class);
  private final ObjectMapper json; private final TableService tables; private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
  public TableWebSocketHandler(ObjectMapper json, TableService tables) { this.json=json; this.tables=tables; }
  @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String tableId = query(session, "tableId"); String playerId = session.getPrincipal() == null ? "" : session.getPrincipal().getName(); sessions.computeIfAbsent(tableId, ignored -> ConcurrentHashMap.newKeySet()).add(session); session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","FULL_STATE_SYNC","payload",tables.snapshot(tableId, playerId)))));
  }
  @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception { String tableId = query(session, "tableId"); String playerId = session.getPrincipal() == null ? "" : session.getPrincipal().getName(); sessions.getOrDefault(tableId, Set.of()).remove(session); if (!tableId.isBlank() && !playerId.isBlank()) { tables.disconnect(tableId, playerId); broadcastTableUpdate(tableId, "PLAYER_DISCONNECTED"); } }
  @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    if (message.getPayloadLength() > 16_384) { session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"code\":\"MESSAGE_TOO_LARGE\"}")); return; }
    JsonNode body = json.readTree(message.getPayload()); String tableId = body.path("tableId").asText(); String playerId = session.getPrincipal() == null ? "" : session.getPrincipal().getName();
    if (body.has("playerId") && !playerId.equals(body.path("playerId").asText())) { log.warn("WebSocket identity mismatch actor={} claimed={}", playerId, body.path("playerId").asText()); session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"code\":\"IDENTITY_MISMATCH\"}")); return; }
    if ("FULL_STATE_SYNC".equals(body.path("type").asText())) { session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","FULL_STATE_SYNC","payload",tables.snapshot(tableId, playerId))))); return; }
    try { Map<String,Object> action = json.convertValue(body, Map.class); tables.apply(tableId, playerId, action); broadcastTableUpdate(tableId, body.path("type").asText()); }
    catch (RuntimeException ex) { session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","ERROR","code",String.valueOf(ex.getMessage()))))); }
  }
  public void broadcastTableUpdate(String tableId, String eventType) { for (WebSocketSession target : sessions.getOrDefault(tableId, Set.of())) { try { if (target.isOpen()) { String actor = target.getPrincipal() == null ? "" : target.getPrincipal().getName(); target.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","TABLE_UPDATE","eventType",eventType,"payload",tables.snapshot(tableId, actor))))); } } catch (Exception ignored) { } } }
  private String query(WebSocketSession s, String name) { String q=s.getUri()==null?null:s.getUri().getQuery(); if(q!=null) for(String p:q.split("&")) { String[] x=p.split("=",2); if(x.length==2&&x[0].equals(name)) return x[1]; } return ""; }
}
