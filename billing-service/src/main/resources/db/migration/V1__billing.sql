CREATE TABLE billing_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE, car_id BIGINT NOT NULL,
 charged_hours INT NOT NULL CHECK(charged_hours>0), hourly_rate DECIMAL(10,2) NOT NULL,
 amount DECIMAL(10,2) NOT NULL CHECK(amount>=0), status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, paid_at TIMESTAMP(6) NULL
);