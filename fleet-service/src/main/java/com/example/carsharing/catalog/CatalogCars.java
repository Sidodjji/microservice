package com.example.carsharing.catalog;

import java.math.BigDecimal;

/** Public API of the Catalog module for other application modules. */
public interface CatalogCars {
    ReservedCar reserve(Long carId);

    void startUsage(Long carId);

    void release(Long carId);

    void requireExisting(Long carId);

    record ReservedCar(Long id, BigDecimal hourlyRate) {
    }
}
