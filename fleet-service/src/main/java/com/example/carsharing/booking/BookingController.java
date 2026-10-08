package com.example.carsharing.booking;

import com.example.carsharing.catalog.CarNotFoundException;
import com.example.carsharing.catalog.CarUnavailableException;
import com.example.carsharing.catalog.IllegalCarStatusTransitionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Booking", description = "Бронювання та оренда автомобілів")
class BookingController {
    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/reserve")
    @Operation(summary = "Забронювати доступний автомобіль")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Автомобіль заброньовано"),
            @ApiResponse(responseCode = "404", description = "Автомобіль не знайдено"),
            @ApiResponse(responseCode = "409", description = "Автомобіль недоступний")})
    BookingResponse reserve(@Valid @RequestBody ReserveBookingRequest request) {
        return bookingService.reserve(request.carId());
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Почати оренду")
    BookingResponse start(@PathVariable Long id) {
        return bookingService.start(id);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Завершити оренду")
    BookingResponse finish(@PathVariable Long id) {
        return bookingService.finish(id);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleBookingNotFound() {
    }

    @ExceptionHandler({CarUnavailableException.class, IllegalCarStatusTransitionException.class,
            IllegalBookingStatusTransitionException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    void handleConflict() {
    }

    @ExceptionHandler(CarNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleCarNotFound() {
    }
}
