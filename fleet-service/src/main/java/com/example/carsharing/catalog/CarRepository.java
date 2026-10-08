package com.example.carsharing.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

interface CarRepository extends JpaRepository<Car, Long> {
}
