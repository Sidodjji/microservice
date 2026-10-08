package com.example.carsharing.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Рахунок за завершену оренду")
public record BillingResponse(Long id, Long bookingId, Long carId, int chargedHours, BigDecimal hourlyRate,
                              BigDecimal amount, BillingStatus status, Instant createdAt, Instant paidAt) {
    static BillingResponse from(BillingRecord record) {
        return new BillingResponse(record.getId(), record.getBookingId(), record.getCarId(), record.getChargedHours(),
                record.getHourlyRate(), record.getAmount(), record.getStatus(), record.getCreatedAt(), record.getPaidAt());
    }
}
