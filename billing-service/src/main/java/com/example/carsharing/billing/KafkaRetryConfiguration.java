package com.example.carsharing.billing;
import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
@Configuration
class KafkaRetryConfiguration {
 // Never acknowledge/drop a failed invoice. Retry after database recovery.
 @Bean DefaultErrorHandler kafkaErrorHandler() {
  var handler=new DefaultErrorHandler(new FixedBackOff(1000L,FixedBackOff.UNLIMITED_ATTEMPTS));
  handler.setClassifications(java.util.Map.of(Exception.class,true),true);
  return handler;
 }
}