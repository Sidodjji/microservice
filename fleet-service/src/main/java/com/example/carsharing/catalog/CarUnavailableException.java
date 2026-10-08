package com.example.carsharing.catalog;

public class CarUnavailableException extends RuntimeException {
    CarUnavailableException(Long carId) {
        super("Car with id %d is not available".formatted(carId));
    }
}
