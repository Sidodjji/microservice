package com.example.carsharing.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
