package com.example.carsharing.billing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/billing")
@Tag(name = "Billing", description = "Рахунки та оплата завершених оренд")
class BillingController {
    private final BillingService billingService;

    BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/history")
    @Operation(summary = "Отримати історію рахунків")
    List<BillingResponse> getHistory() {
        return billingService.getHistory();
    }

    @PostMapping("/pay")
    @Operation(summary = "Оплатити рахунок")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Рахунок оплачено"),
            @ApiResponse(responseCode = "404", description = "Рахунок не знайдено"),
            @ApiResponse(responseCode = "409", description = "Рахунок вже оплачено")})
    BillingResponse pay(@Valid @RequestBody PayBillingRequest request) {
        return billingService.pay(request.billingId());
    }

    @ExceptionHandler(BillingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void handleNotFound() {
    }

    @ExceptionHandler(BillingAlreadyPaidException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    void handleAlreadyPaid() {
    }
}
