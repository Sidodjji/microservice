package com.example.carsharing.messaging;
import com.example.carsharing.contracts.BookingFinishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
@Component
public class OutboxEvents {
 private final JdbcTemplate jdbc;
 private final JsonMapper json;
 public OutboxEvents(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc; this.json=json; }
 @EventListener
 @Transactional(propagation=Propagation.MANDATORY)
 public void save(BookingFinishedEvent event) {
  jdbc.update("INSERT INTO outbox_events (booking_id,payload) VALUES (?,?)",event.bookingId(),json.writeValueAsString(event));
 }
}