package com.example.carsharing.identity;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
