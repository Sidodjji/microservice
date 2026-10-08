package com.example.carsharing.booking;

import org.springframework.data.jpa.repository.JpaRepository;

interface BookingRepository extends JpaRepository<Booking, Long> {
}
