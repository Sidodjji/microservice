package com.example.carsharing.booking;

import com.example.carsharing.catalog.CatalogCars;
import com.example.carsharing.contracts.BookingFinishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
class BookingService {
    private final BookingRepository bookingRepository;
    private final CatalogCars catalogCars;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    BookingService(BookingRepository bookingRepository, CatalogCars catalogCars,
                   ApplicationEventPublisher eventPublisher) {
        this(bookingRepository, catalogCars, eventPublisher, Clock.systemUTC());
    }

    BookingService(BookingRepository bookingRepository, CatalogCars catalogCars,
                   ApplicationEventPublisher eventPublisher, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.catalogCars = catalogCars;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    BookingResponse reserve(Long carId) {
        CatalogCars.ReservedCar car = catalogCars.reserve(carId);
        return BookingResponse.from(bookingRepository.save(new Booking(car.id(), car.hourlyRate())));
    }

    @Transactional
    BookingResponse start(Long bookingId) {
        Booking booking = findBooking(bookingId);
        catalogCars.startUsage(booking.getCarId());
        booking.start(Instant.now(clock));
        return BookingResponse.from(booking);
    }

    @Transactional
    BookingResponse finish(Long bookingId) {
        Booking booking = findBooking(bookingId);
        Instant finishedAt = Instant.now(clock);
        booking.finish(finishedAt);
        catalogCars.release(booking.getCarId());
        eventPublisher.publishEvent(new BookingFinishedEvent(booking.getId(), booking.getCarId(), booking.getHourlyRate(),
                booking.getStartedAt(), booking.getFinishedAt()));
        return BookingResponse.from(booking);
    }

    private Booking findBooking(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }
}
