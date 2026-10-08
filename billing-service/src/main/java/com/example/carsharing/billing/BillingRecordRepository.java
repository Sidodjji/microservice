package com.example.carsharing.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<BillingRecord> findById(Long id);
    boolean existsByBookingId(Long bookingId);
    List<BillingRecord> findAllByOrderByCreatedAtDesc();
}
