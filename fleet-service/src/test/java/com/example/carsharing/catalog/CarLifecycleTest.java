package com.example.carsharing.catalog;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class CarLifecycleTest {
 @Test void enforcesEveryTransition() {
  Car car=new Car("Tesla","Model 3","AA0001AA",new BigDecimal("320.00"));
  assertThat(car.getStatus()).isEqualTo(CarStatus.AVAILABLE);
  assertThatThrownBy(car::startUsage).isInstanceOf(IllegalCarStatusTransitionException.class);
  assertThatThrownBy(car::release).isInstanceOf(IllegalCarStatusTransitionException.class);
  car.reserve();
  assertThatThrownBy(car::reserve).isInstanceOf(CarUnavailableException.class);
  assertThatThrownBy(car::release).isInstanceOf(IllegalCarStatusTransitionException.class);
  car.startUsage();
  assertThatThrownBy(car::reserve).isInstanceOf(CarUnavailableException.class);
  assertThatThrownBy(car::startUsage).isInstanceOf(IllegalCarStatusTransitionException.class);
  car.release();
  assertThat(car.getStatus()).isEqualTo(CarStatus.AVAILABLE);
 }
}