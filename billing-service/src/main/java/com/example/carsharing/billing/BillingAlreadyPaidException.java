package com.example.carsharing.billing;

class BillingAlreadyPaidException extends RuntimeException {
    BillingAlreadyPaidException(Long id) {
        super("Billing record with id %d has already been paid".formatted(id));
    }
}
