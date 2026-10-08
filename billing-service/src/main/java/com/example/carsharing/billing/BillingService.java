package com.example.carsharing.billing;

import com.example.carsharing.contracts.BookingFinishedEvent;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
class BillingService {
    private final BillingRecordRepository billingRecordRepository;
    private final Clock clock;

    @Autowired
    BillingService(BillingRecordRepository billingRecordRepository) {
        this(billingRecordRepository, Clock.systemUTC());
    }

    BillingService(BillingRecordRepository billingRecordRepository, Clock clock) {
        this.billingRecordRepository = billingRecordRepository;
        this.clock = clock;
    }

    
    @Transactional
    void createBillingRecord(BookingFinishedEvent event) {
        if (billingRecordRepository.existsByBookingId(event.bookingId())) {
            return;
        }
        billingRecordRepository.save(new BillingRecord(event.bookingId(), event.carId(), calculateChargedHours(event),
                event.hourlyRate(), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    List<BillingResponse> getHistory() {
        return billingRecordRepository.findAllByOrderByCreatedAtDesc().stream().map(BillingResponse::from).toList();
    }

    @Transactional
    BillingResponse pay(Long billingId) {
        BillingRecord record = billingRecordRepository.findById(billingId)
                .orElseThrow(() -> new BillingNotFoundException(billingId));
        record.pay(Instant.now(clock));
        return BillingResponse.from(record);
    }

    static int calculateChargedHours(BookingFinishedEvent event) {
        long seconds = Duration.between(event.startedAt(), event.finishedAt()).toSeconds();
        long roundedUpHours = Math.max(1, (seconds + 3_599) / 3_600);
        return Math.toIntExact(roundedUpHours);
    }
}
