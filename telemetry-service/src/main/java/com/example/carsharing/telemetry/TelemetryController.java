package com.example.carsharing.telemetry;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry", description = "Остання GPS-позиція автомобіля")
class TelemetryController {
    private final TelemetryService telemetryService;

    TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Зберегти останню GPS-позицію автомобіля")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Позицію збережено"),
            @ApiResponse(responseCode = "404", description = "Автомобіль не знайдено")})
    void saveLocation(@Valid @RequestBody LocationRequest request) {
        telemetryService.recordLocation(request);
    }

    @ExceptionHandler(CarNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleCarNotFound() {
    }
}
