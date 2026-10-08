package com.example.carsharing.catalog;

public class CarNotFoundException extends RuntimeException {
    CarNotFoundException(Long id) {
        super("Car with id %d was not found".formatted(id));
    }
}
