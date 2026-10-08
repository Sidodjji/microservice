package com.example.carsharing.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "car_id", nullable = false)
    private Long carId;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    private long version;

    protected Booking() {
    }

    Booking(Long carId, BigDecimal hourlyRate) {
        this.carId = carId;
        this.hourlyRate = hourlyRate;
        this.status = BookingStatus.RESERVED;
    }

    Long getId() { return id; }
    Long getCarId() { return carId; }
    BigDecimal getHourlyRate() { return hourlyRate; }
    BookingStatus getStatus() { return status; }
    Instant getStartedAt() { return startedAt; }
    Instant getFinishedAt() { return finishedAt; }

    void start(Instant startedAt) {
        ensureStatus(BookingStatus.RESERVED, BookingStatus.ACTIVE);
        this.status = BookingStatus.ACTIVE;
        this.startedAt = startedAt;
    }

    void finish(Instant finishedAt) {
        ensureStatus(BookingStatus.ACTIVE, BookingStatus.FINISHED);
        this.status = BookingStatus.FINISHED;
        this.finishedAt = finishedAt;
    }

    private void ensureStatus(BookingStatus expected, BookingStatus target) {
        if (status != expected) {
            throw new IllegalBookingStatusTransitionException(id, status, target);
        }
    }
}
