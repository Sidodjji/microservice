package com.example.carsharing.billing;

import com.example.carsharing.contracts.BookingFinishedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCalculationTest {
    @Test
    void roundsAUsagePeriodUpToTheNearestHour() throws Exception {
        BookingFinishedEvent event = new BookingFinishedEvent(1L, 1L, BigDecimal.valueOf(320),
                Instant.parse("2026-09-21T10:00:00Z"), Instant.parse("2026-09-21T11:01:00Z"));

        assertThat(BillingService.calculateChargedHours(event)).isEqualTo(2);
    }
}
