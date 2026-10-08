package com.example.carsharing.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface CarLocationRepository extends JpaRepository<CarLocation, Long> {
    Optional<CarLocation> findByCarId(Long carId);
}
