package com.example.carsharing.catalog;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {

    @Test
    void throwsNotFoundForUnknownCar() {
        CarRepository repository = mock(CarRepository.class);
        when(repository.findById(anyLong())).thenReturn(Optional.empty());
        CatalogService service = new CatalogService(repository);

        assertThatThrownBy(() -> service.getCar(42L))
                .isInstanceOf(CarNotFoundException.class)
                .hasMessage("Car with id 42 was not found");
    }
}
