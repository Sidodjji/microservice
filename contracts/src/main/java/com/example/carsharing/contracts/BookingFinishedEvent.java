package com.example.carsharing.contracts;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingFinishedEvent(Long bookingId, Long carId, BigDecimal hourlyRate, Instant startedAt, Instant finishedAt) {
}
