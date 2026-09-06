package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Zona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZonaRepository extends JpaRepository<Zona,Long> {
    List<Zona> findByDistrito(String distrito);
}
