package com.example.carsharing.messaging;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class OutboxRelay {
 private final JdbcTemplate jdbc;
 private final KafkaTemplate<String,String> kafka;
 private final String topic;
 public OutboxRelay(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,@Value("${app.kafka.booking-finished-topic}") String topic) {
  this.jdbc=jdbc; this.kafka=kafka; this.topic=topic;
 }
 @Scheduled(fixedDelayString="${app.outbox.poll-ms:1000}")
 @Transactional
 public void publish() {
  var events=jdbc.query("SELECT id,booking_id,payload FROM outbox_events WHERE published_at IS NULL ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED",
   (rs,n) -> new Pending(rs.getLong(1),rs.getLong(2),rs.getString(3)));
  for(var event:events) {
   try { kafka.send(topic,Long.toString(event.bookingId()),event.payload()).get(10,TimeUnit.SECONDS); }
   catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Outbox publication interrupted",e); }
   catch(Exception e) { throw new IllegalStateException("Kafka publication failed; outbox retained for retry",e); }
   jdbc.update("UPDATE outbox_events SET published_at=CURRENT_TIMESTAMP(6) WHERE id=?",event.id());
  }
 }
 private record Pending(long id,long bookingId,String payload) {}
}