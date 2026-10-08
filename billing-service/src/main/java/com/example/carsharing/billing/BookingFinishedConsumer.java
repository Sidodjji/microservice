package com.example.carsharing.billing;
import com.example.carsharing.contracts.BookingFinishedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component
class BookingFinishedConsumer {
 private final BillingService service;
 private final JsonMapper json;
 BookingFinishedConsumer(BillingService service,JsonMapper json) { this.service=service;this.json=json; }
 @KafkaListener(topics="${app.kafka.booking-finished-topic}",groupId="billing-service-v1")
 void consume(String payload) { service.createBillingRecord(json.readValue(payload,BookingFinishedEvent.class)); }
}