package com.pokernight.ws;

import com.fasterxml.jackson.databind.*;
import com.pokernight.table.TableService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;

@Component
public class TableWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper json; private final TableService tables;
  public TableWebSocketHandler(ObjectMapper json, TableService tables) { this.json=json; this.tables=tables; }
  @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String tableId = query(session, "tableId"); session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","FULL_STATE_SYNC","payload",tables.snapshot(tableId)))));
  }
  @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode body = json.readTree(message.getPayload()); String tableId = body.path("tableId").asText(); String playerId = body.path("playerId").asText();
    try { Map<String,Object> action = json.convertValue(body, Map.class); session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","TABLE_UPDATE","eventType",body.path("type").asText(),"payload",tables.apply(tableId, playerId, action))))); }
    catch (IllegalStateException ex) { session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","ERROR","code",ex.getMessage())))); }
  }
  private String query(WebSocketSession s, String name) { String q=s.getUri()==null?null:s.getUri().getQuery(); if(q!=null) for(String p:q.split("&")) { String[] x=p.split("=",2); if(x.length==2&&x[0].equals(name)) return x[1]; } return ""; }
}
