CREATE TABLE cars (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, brand VARCHAR(100) NOT NULL, model VARCHAR(100) NOT NULL,
 registration_number VARCHAR(20) NOT NULL UNIQUE, hourly_rate DECIMAL(10,2) NOT NULL CHECK(hourly_rate>=0),
 status VARCHAR(30) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE bookings (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, car_id BIGINT NOT NULL, hourly_rate DECIMAL(10,2) NOT NULL CHECK(hourly_rate>=0),
 status VARCHAR(30) NOT NULL, started_at TIMESTAMP(6) NULL, finished_at TIMESTAMP(6) NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE outbox_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE, payload TEXT NOT NULL,
 published_at TIMESTAMP(6) NULL, INDEX ix_outbox_pending(published_at,id)
);
INSERT INTO cars(brand,model,registration_number,hourly_rate,status) VALUES
 ('Tesla','Model 3','AA0001AA',320.00,'AVAILABLE'), ('Toyota','Corolla','AA0002AA',180.00,'AVAILABLE');