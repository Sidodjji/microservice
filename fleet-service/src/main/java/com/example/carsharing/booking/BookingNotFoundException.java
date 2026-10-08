package com.example.carsharing.booking;

class BookingNotFoundException extends RuntimeException {
    BookingNotFoundException(Long id) {
        super("Booking with id %d was not found".formatted(id));
    }
}
