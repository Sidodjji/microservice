package com.example.carsharing.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cars")
@Tag(name = "Catalog", description = "Каталог автомобілів")
class CatalogController {
    private final CatalogService catalogService;

    CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "Отримати список автомобілів")
    List<CarResponse> getCars() {
        return catalogService.getCars();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати автомобіль за ідентифікатором")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Автомобіль знайдено"),
            @ApiResponse(responseCode = "404", description = "Автомобіль не знайдено")
    })
    CarResponse getCar(@PathVariable Long id) {
        return catalogService.getCar(id);
    }

    @PostMapping
    @Operation(summary = "Додати автомобіль до каталогу")
    @ResponseStatus(HttpStatus.CREATED)
    CarResponse createCar(@jakarta.validation.Valid @RequestBody CreateCarRequest request) {
        return catalogService.createCar(request);
    }

    @ExceptionHandler(CarNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleNotFound() {
    }
}
