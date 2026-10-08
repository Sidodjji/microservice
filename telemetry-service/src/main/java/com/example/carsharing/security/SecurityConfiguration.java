package com.example.carsharing.security;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfiguration {
 @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
  return http.csrf(c -> c.disable())
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a -> a.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/actuator/health").permitAll()
    .requestMatchers(HttpMethod.GET,"/api/cars/**").permitAll().anyRequest().authenticated())
   .oauth2ResourceServer(r -> r.jwt(Customizer.withDefaults())).build();
 }
 @Bean JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret) {
  byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
  if(bytes.length<32) throw new IllegalStateException("JWT secret must contain at least 32 bytes");
  return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes,"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
 }
}