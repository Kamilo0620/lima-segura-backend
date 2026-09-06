package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria,Long> {

    Optional<Categoria> findByNombre(String nombre);

    boolean existsByNombre(String nombre);
}
