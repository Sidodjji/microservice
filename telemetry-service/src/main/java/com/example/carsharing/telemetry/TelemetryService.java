package com.example.carsharing.telemetry;


import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
class TelemetryService {
    private final CarLocationRepository carLocationRepository;
    private final CatalogClient catalogCars;
    private final Clock clock;

    @Autowired
    TelemetryService(CarLocationRepository carLocationRepository, CatalogClient catalogCars) {
        this(carLocationRepository, catalogCars, Clock.systemUTC());
    }

    TelemetryService(CarLocationRepository carLocationRepository, CatalogClient catalogCars, Clock clock) {
        this.carLocationRepository = carLocationRepository;
        this.catalogCars = catalogCars;
        this.clock = clock;
    }

    @Transactional
    void recordLocation(LocationRequest request) {
        catalogCars.requireExisting(request.carId());
        Instant recordedAt = Instant.now(clock);
        carLocationRepository.findByCarId(request.carId())
                .ifPresentOrElse(location -> location.update(request.latitude(), request.longitude(), recordedAt),
                        () -> carLocationRepository.save(new CarLocation(request.carId(), request.latitude(),
                                request.longitude(), recordedAt)));
    }
}
