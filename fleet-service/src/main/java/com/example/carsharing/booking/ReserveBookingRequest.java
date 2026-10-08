package com.example.carsharing.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveBookingRequest(@NotNull @Positive Long carId) {
}
