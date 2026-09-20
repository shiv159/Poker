package com.pokernight.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.security.Principal;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;

public class PlayerHandshakeInterceptor implements HandshakeInterceptor {
  private final StringRedisTemplate redis;
  public PlayerHandshakeInterceptor(StringRedisTemplate redis) { this.redis = redis; }
  @Override public boolean beforeHandshake(ServerHttpRequest request, org.springframework.http.server.ServerHttpResponse response, WebSocketHandler handler, Map<String,Object> attributes) {
    String query = request.getURI().getQuery(); if (query == null) return false; String token = null;
    for (String part : query.split("&")) { String[] pair = part.split("=", 2); if (pair.length == 2 && pair[0].equals("sessionToken")) token = pair[1]; }
    if (token != null && !token.isBlank()) { String playerId = redis.opsForValue().get("table:" + tableId(query) + ":session:" + token); if (playerId != null && !playerId.isBlank()) { attributes.put("playerId", playerId); return true; } }
    return false;
  }
  @Override public void afterHandshake(ServerHttpRequest request, org.springframework.http.server.ServerHttpResponse response, WebSocketHandler handler, Exception exception) {}
  public static Principal principal(Map<String,Object> attributes) { return () -> String.valueOf(attributes.get("playerId")); }
  private static String tableId(String query) { for (String part : query.split("&")) { String[] pair=part.split("=",2); if(pair.length==2 && pair[0].equals("tableId")) return pair[1]; } return ""; }
}
