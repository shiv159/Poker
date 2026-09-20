package com.pokernight.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.Arrays;

@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {
  @Value("${app.cors-origins:http://localhost:4200,http://localhost:8088}") private String origins;
  @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/**").allowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(origin -> !origin.isBlank()).toArray(String[]::new)).allowedMethods("GET","POST","OPTIONS").allowedHeaders("*"); }
}
