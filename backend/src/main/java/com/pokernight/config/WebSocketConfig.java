package com.pokernight.config;

import com.pokernight.ws.TableWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final TableWebSocketHandler handler;
  public WebSocketConfig(TableWebSocketHandler handler) { this.handler = handler; }
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/table").addInterceptors(new HttpSessionHandshakeInterceptor()).setAllowedOrigins("http://localhost:4200");
  }
}
