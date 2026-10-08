package com.example.carsharing.catalog;

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

@Entity
@Table(name = "cars")
class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    @Column(name = "registration_number", nullable = false, unique = true)
    private String registrationNumber;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarStatus status;

    @Version
    private long version;

    protected Car() {
    }

    Car(String brand, String model, String registrationNumber, BigDecimal hourlyRate) {
        this.brand = brand;
        this.model = model;
        this.registrationNumber = registrationNumber;
        this.hourlyRate = hourlyRate;
        this.status = CarStatus.AVAILABLE;
    }

    Long getId() { return id; }
    String getBrand() { return brand; }
    String getModel() { return model; }
    String getRegistrationNumber() { return registrationNumber; }
    BigDecimal getHourlyRate() { return hourlyRate; }
    CarStatus getStatus() { return status; }

    void reserve() {
        if (status != CarStatus.AVAILABLE) {
            throw new CarUnavailableException(id);
        }
        status = CarStatus.RESERVED;
    }

    void startUsage() {
        if (status != CarStatus.RESERVED) {
            throw new IllegalCarStatusTransitionException(id, status, CarStatus.IN_USE);
        }
        status = CarStatus.IN_USE;
    }

    void release() {
        if (status != CarStatus.IN_USE) {
            throw new IllegalCarStatusTransitionException(id, status, CarStatus.AVAILABLE);
        }
        status = CarStatus.AVAILABLE;
    }
}
