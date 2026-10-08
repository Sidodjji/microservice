package com.example.carsharing.booking;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Бронювання автомобіля")
public record BookingResponse(Long id, Long carId, BigDecimal hourlyRate, BookingStatus status,
                              Instant startedAt, Instant finishedAt) {
    static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.getId(), booking.getCarId(), booking.getHourlyRate(), booking.getStatus(),
                booking.getStartedAt(), booking.getFinishedAt());
    }
}
