package com.example.carsharing.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_records")
class BillingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "car_id", nullable = false)
    private Long carId;

    @Column(name = "charged_hours", nullable = false)
    private int chargedHours;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected BillingRecord() {
    }

    BillingRecord(Long bookingId, Long carId, int chargedHours, BigDecimal hourlyRate, Instant createdAt) {
        this.bookingId = bookingId;
        this.carId = carId;
        this.chargedHours = chargedHours;
        this.hourlyRate = hourlyRate;
        this.amount = hourlyRate.multiply(BigDecimal.valueOf(chargedHours));
        this.status = BillingStatus.PENDING;
        this.createdAt = createdAt;
    }

    Long getId() { return id; }
    Long getBookingId() { return bookingId; }
    Long getCarId() { return carId; }
    int getChargedHours() { return chargedHours; }
    BigDecimal getHourlyRate() { return hourlyRate; }
    BigDecimal getAmount() { return amount; }
    BillingStatus getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getPaidAt() { return paidAt; }

    void pay(Instant paidAt) {
        if (status != BillingStatus.PENDING) {
            throw new BillingAlreadyPaidException(id);
        }
        status = BillingStatus.PAID;
        this.paidAt = paidAt;
    }
}
