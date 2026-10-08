package com.example.carsharing.messaging;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.Hidden
@RestControllerAdvice
class ConcurrencyAdvice {
 @ExceptionHandler({ObjectOptimisticLockingFailureException.class, org.springframework.dao.DuplicateKeyException.class})
 @ResponseStatus(HttpStatus.CONFLICT)
 void conflict() {}
}