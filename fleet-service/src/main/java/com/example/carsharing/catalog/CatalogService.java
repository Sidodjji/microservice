package com.example.carsharing.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class CatalogService implements CatalogCars {
    private final CarRepository carRepository;

    CatalogService(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    List<CarResponse> getCars() {
        return carRepository.findAll().stream().map(CarResponse::from).toList();
    }

    CarResponse getCar(Long id) {
        return carRepository.findById(id).map(CarResponse::from)
                .orElseThrow(() -> new CarNotFoundException(id));
    }

    @Transactional
    CarResponse createCar(CreateCarRequest request) {
        Car car = new Car(request.brand(), request.model(), request.registrationNumber(), request.hourlyRate());
        return CarResponse.from(carRepository.save(car));
    }

    @Override
    @Transactional
    public ReservedCar reserve(Long carId) {
        Car car = findCar(carId);
        car.reserve();
        return new ReservedCar(car.getId(), car.getHourlyRate());
    }

    @Override
    @Transactional
    public void startUsage(Long carId) {
        findCar(carId).startUsage();
    }

    @Override
    @Transactional
    public void release(Long carId) {
        findCar(carId).release();
    }

    @Override
    public void requireExisting(Long carId) {
        findCar(carId);
    }

    private Car findCar(Long id) {
        return carRepository.findById(id).orElseThrow(() -> new CarNotFoundException(id));
    }
}
