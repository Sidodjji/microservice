package com.example.carsharing.identity;

class EmailAlreadyRegisteredException extends RuntimeException {
    EmailAlreadyRegisteredException(String email) {
        super("A user with email %s already exists".formatted(email));
    }
}
