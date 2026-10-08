package com.example.carsharing.booking;

class IllegalBookingStatusTransitionException extends RuntimeException {
    IllegalBookingStatusTransitionException(Long bookingId, BookingStatus current, BookingStatus target) {
        super("Booking with id %d cannot change status from %s to %s".formatted(bookingId, current, target));
    }
}
