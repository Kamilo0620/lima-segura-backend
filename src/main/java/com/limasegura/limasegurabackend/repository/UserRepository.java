package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Integer countByConfirmationId();
    Integer countByReportId();
}
