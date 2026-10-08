package com.example.carsharing.booking;
import com.example.carsharing.catalog.CatalogCars;
import com.example.carsharing.contracts.BookingFinishedEvent;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingLifecycleTest {
 @Test void rejectsFinishBeforeStartAndRepeatedTransitions() {
  var booking=new Booking(7L,new BigDecimal("320.00"));
  var now=Instant.parse("2026-09-21T10:00:00Z");
  assertThatThrownBy(()->booking.finish(now)).isInstanceOf(IllegalBookingStatusTransitionException.class);
  booking.start(now);
  assertThatThrownBy(()->booking.start(now)).isInstanceOf(IllegalBookingStatusTransitionException.class);
  booking.finish(now.plusSeconds(3601));
  assertThatThrownBy(()->booking.finish(now)).isInstanceOf(IllegalBookingStatusTransitionException.class);
  assertThat(booking.getStartedAt()).isEqualTo(now);
  assertThat(booking.getStatus()).isEqualTo(BookingStatus.FINISHED);
 }
 @Test void finishReleasesCarAndPublishesOriginalRateAndTimes() {
  var repo=mock(BookingRepository.class); var cars=mock(CatalogCars.class);
  var publisher=mock(ApplicationEventPublisher.class);
  var start=Instant.parse("2026-09-21T10:00:00Z");
  var end=start.plusSeconds(3601);
  var booking=new Booking(7L,new BigDecimal("320.00")); booking.start(start);
  when(repo.findById(5L)).thenReturn(Optional.of(booking));
  var service=new BookingService(repo,cars,publisher,Clock.fixed(end,ZoneOffset.UTC));
  assertThat(service.finish(5L).status()).isEqualTo(BookingStatus.FINISHED);
  verify(cars).release(7L);
  verify(publisher).publishEvent(new BookingFinishedEvent(null,7L,new BigDecimal("320.00"),start,end));
 }
 @Test void unknownBookingDoesNotChangeCar() {
  var repo=mock(BookingRepository.class);var cars=mock(CatalogCars.class);
  when(repo.findById(5L)).thenReturn(Optional.empty());
  var service=new BookingService(repo,cars,mock(ApplicationEventPublisher.class));
  assertThatThrownBy(()->service.start(5L)).isInstanceOf(BookingNotFoundException.class);
  verifyNoInteractions(cars);
 }
}