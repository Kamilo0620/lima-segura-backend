package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.ZoneSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneSubscriptionRepository extends JpaRepository<ZoneSubscription, Long> {
    List<ZoneSubscription> findByDistrictUbigeo(String ubigeo);
    List<ZoneSubscription> findByUserId(Long userId);
}
