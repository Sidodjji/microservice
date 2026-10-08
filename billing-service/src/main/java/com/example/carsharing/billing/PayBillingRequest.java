package com.example.carsharing.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PayBillingRequest(@NotNull @Positive Long billingId) {
}
