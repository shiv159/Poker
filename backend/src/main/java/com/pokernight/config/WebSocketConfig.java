package com.pokernight.config;

import com.pokernight.ws.TableWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final TableWebSocketHandler handler; private final StringRedisTemplate redis; private final String allowedOrigins;
  public WebSocketConfig(TableWebSocketHandler handler, StringRedisTemplate redis, @Value("${app.websocket-allowed-origins:http://localhost:4200,http://localhost:8088}") String allowedOrigins) { this.handler = handler; this.redis = redis; this.allowedOrigins = allowedOrigins; }
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    DefaultHandshakeHandler handshake = new DefaultHandshakeHandler() { @Override protected java.security.Principal determineUser(org.springframework.http.server.ServerHttpRequest request, org.springframework.web.socket.WebSocketHandler wsHandler, Map<String,Object> attributes) { return PlayerHandshakeInterceptor.principal(attributes); } };
    registry.addHandler(handler, "/ws/table").addInterceptors(new PlayerHandshakeInterceptor(redis)).setHandshakeHandler(handshake).setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(origin -> !origin.isBlank()).toArray(String[]::new));
  }
}
