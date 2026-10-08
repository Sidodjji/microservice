package com.example.carsharing.billing;

class BillingNotFoundException extends RuntimeException {
    BillingNotFoundException(Long id) {
        super("Billing record with id %d was not found".formatted(id));
    }
}
