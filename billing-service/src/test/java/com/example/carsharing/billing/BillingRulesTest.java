package com.example.carsharing.billing;
import com.example.carsharing.contracts.BookingFinishedEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BillingRulesTest {
 @ParameterizedTest @CsvSource({"0,1","1,1","3599,1","3600,1","3601,2","7200,2","7201,3"})
 void preservesRounding(long seconds,int hours) {
  var start=Instant.parse("2026-01-01T00:00:00Z");
  assertThat(BillingService.calculateChargedHours(new BookingFinishedEvent(1L,2L,BigDecimal.TEN,start,start.plusSeconds(seconds)))).isEqualTo(hours);
 }
 @Test void repeatedDeliveryDoesNotCreateAnotherBill() {
  var repo=mock(BillingRecordRepository.class); when(repo.existsByBookingId(1L)).thenReturn(true);
  new BillingService(repo).createBillingRecord(new BookingFinishedEvent(1L,2L,BigDecimal.TEN,Instant.EPOCH,Instant.EPOCH));
  verify(repo,never()).save(any());
 }
 @Test void amountAndPaymentArePreserved() {
  var now=Instant.parse("2026-01-01T00:00:00Z");
  var record=new BillingRecord(1L,2L,2,new BigDecimal("320.00"),now);
  assertThat(record.getAmount()).isEqualByComparingTo("640.00");
  assertThat(record.getStatus()).isEqualTo(BillingStatus.PENDING);
  record.pay(now.plusSeconds(1));
  assertThat(record.getStatus()).isEqualTo(BillingStatus.PAID);
  assertThat(record.getPaidAt()).isEqualTo(now.plusSeconds(1));
  assertThatThrownBy(()->record.pay(now)).isInstanceOf(BillingAlreadyPaidException.class);
 }
 @Test void missingBillIsNotFound() {
  var repo=mock(BillingRecordRepository.class);when(repo.findById(1L)).thenReturn(Optional.empty());
  assertThatThrownBy(()->new BillingService(repo).pay(1L)).isInstanceOf(BillingNotFoundException.class);
 }
}