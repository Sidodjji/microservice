package com.example.carsharing.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "car_locations")
class CarLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "car_id", nullable = false, unique = true)
    private Long carId;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected CarLocation() {
    }

    CarLocation(Long carId, BigDecimal latitude, BigDecimal longitude, Instant recordedAt) {
        this.carId = carId;
        update(latitude, longitude, recordedAt);
    }

    void update(BigDecimal latitude, BigDecimal longitude, Instant recordedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }
}
