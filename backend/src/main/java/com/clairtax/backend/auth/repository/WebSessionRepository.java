package com.clairtax.backend.auth.repository;

import com.clairtax.backend.auth.entity.WebSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebSessionRepository extends JpaRepository<WebSession, UUID> {

    Optional<WebSession> findBySessionHash(String sessionHash);
}
