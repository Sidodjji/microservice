package com.example.carsharing.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Автомобіль, доступний у каршерінгу")
public record CarResponse(
        Long id,
        String brand,
        String model,
        String registrationNumber,
        BigDecimal hourlyRate,
        CarStatus status
) {
    static CarResponse from(Car car) {
        return new CarResponse(car.getId(), car.getBrand(), car.getModel(), car.getRegistrationNumber(),
                car.getHourlyRate(), car.getStatus());
    }
}
