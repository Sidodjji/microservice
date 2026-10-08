package com.example.carsharing.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateCarRequest(
        @NotBlank @Size(max = 100) String brand,
        @NotBlank @Size(max = 100) String model,
        @NotBlank @Size(max = 20) String registrationNumber,
        @NotNull @DecimalMin("0.00") BigDecimal hourlyRate
) {
}
