package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Confirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {

    boolean existsByIncidentIdAndUserId(Long incidentId, Long userId);

    long countByIncidentId(Long incidentId);
}
