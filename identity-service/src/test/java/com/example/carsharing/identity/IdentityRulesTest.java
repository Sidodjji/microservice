package com.example.carsharing.identity;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IdentityRulesTest {
 @Test void registrationHashesPasswordAndRejectsDuplicates() {
  var repo=mock(AppUserRepository.class);var tokens=mock(JwtTokenService.class);
  var service=new AuthService(repo,new BCryptPasswordEncoder(),mock(AuthenticationManager.class),tokens);
  service.register(new RegisterRequest("test@example.com","password123"));
  var captor=org.mockito.ArgumentCaptor.forClass(AppUser.class);verify(repo).save(captor.capture());
  assertThat(new BCryptPasswordEncoder().matches("password123",captor.getValue().getPasswordHash())).isTrue();
  verify(tokens).issueToken("test@example.com");
  when(repo.existsByEmail("test@example.com")).thenReturn(true);
  assertThatThrownBy(()->service.register(new RegisterRequest("test@example.com","password123"))).isInstanceOf(EmailAlreadyRegisteredException.class);
 }
 @Test void tokenUsesHs256EmailSubjectAndOneHourExpiration() {
  var security=new SecurityConfiguration();
  var key=security.jwtSecretKey("test-secret-at-least-thirty-two-bytes-long");
  var now=Instant.now();
  var response=new JwtTokenService(security.jwtEncoder(key),Clock.fixed(now,ZoneOffset.UTC),60).issueToken("test@example.com");
  var decoded=security.jwtDecoder(key).decode(response.accessToken());
  assertThat(decoded.getSubject()).isEqualTo("test@example.com");
  assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
  assertThat(Duration.between(decoded.getIssuedAt(),decoded.getExpiresAt())).isEqualTo(Duration.ofHours(1));
  assertThat(response.tokenType()).isEqualTo("Bearer");
  assertThat(response.expiresInSeconds()).isEqualTo(3600);
 }
}