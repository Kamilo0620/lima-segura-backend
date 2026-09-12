package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByDistrict(String district);
}