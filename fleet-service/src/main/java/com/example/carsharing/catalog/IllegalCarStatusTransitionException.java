package com.example.carsharing.catalog;

public class IllegalCarStatusTransitionException extends RuntimeException {
    IllegalCarStatusTransitionException(Long carId, CarStatus currentStatus, CarStatus targetStatus) {
        super("Car with id %d cannot change status from %s to %s".formatted(carId, currentStatus, targetStatus));
    }
}
