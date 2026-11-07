package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, String> {

    @Query("SELECT d FROM District d WHERE LOWER(d.name) = LOWER(:name) AND LOWER(d.department) = LOWER(:department)")
    Optional<District> findByNameAndDepartmentIgnoreCase(@Param("name") String name, @Param("department") String department);

    @Query("SELECT d FROM District d WHERE LOWER(d.name) = LOWER(:name)")
    List<District> findByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT d FROM District d WHERE LOWER(d.name) = LOWER(:name) AND LOWER(d.province) = LOWER(:province)")
    Optional<District> findByNameAndProvinceIgnoreCase(@Param("name") String name, @Param("province") String province);
}
