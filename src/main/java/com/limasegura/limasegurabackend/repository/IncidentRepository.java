package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.IncidentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    Page<Incident> findByStatus(IncidentStatus status, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE " +
           "(:type IS NULL OR i.incidentType = :type) AND " +
           "(:from IS NULL OR i.occurredAt >= :from) AND " +
           "(:to IS NULL OR i.occurredAt <= :to) AND " +
           "(:district IS NULL OR i.district.ubigeo = :district) AND " +
           "(:status IS NULL OR i.status = :status) AND " +
           "(:origin IS NULL OR i.origin = :origin)")
    Page<Incident> findFiltered(
        @Param("type") IncidentType type,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("district") String district,
        @Param("status") IncidentStatus status,
        @Param("origin") com.limasegura.limasegurabackend.model.IncidentOrigin origin,
        Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.status = 'PUBLISHED' AND " +
           "i.incidentType = :type AND " +
           "i.district.ubigeo = :districtUbigeo AND " +
           "i.occurredAt BETWEEN :from AND :to")
    List<Incident> findDuplicateCandidates(
        @Param("type") IncidentType type,
        @Param("districtUbigeo") String districtUbigeo,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT DISTINCT i FROM Incident i " +
           "LEFT JOIN FETCH i.sourceItems si " +
           "LEFT JOIN FETCH si.newsSource ns " +
           "WHERE i.status = 'PUBLISHED' " +
           "AND i.lat IS NOT NULL AND i.lng IS NOT NULL " +
           "AND (:from IS NULL OR i.occurredAt >= :from) " +
           "AND (:to IS NULL OR i.occurredAt <= :to) " +
           "AND (:types IS NULL OR i.incidentType IN :types) " +
           "AND (:minConfidence IS NULL OR i.confidence >= :minConfidence)")
    List<Incident> findHeatmapIncidents(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("types") List<IncidentType> types,
        @Param("minConfidence") Double minConfidence);

    @Query(value = "SELECT ROUND(CAST(i.lat AS numeric), 3) as lat, ROUND(CAST(i.lng AS numeric), 3) as lng, SUM(i.incident_count) as weight " +
                   "FROM incidents i WHERE i.status = 'PUBLISHED' " +
                   "AND (:from IS NULL OR i.occurred_at >= CAST(:from AS timestamp)) " +
                   "AND (:to IS NULL OR i.occurred_at <= CAST(:to AS timestamp)) " +
                   "AND (:types IS NULL OR i.incident_type IN (:types)) " +
                   "AND (:minConfidence IS NULL OR i.confidence >= CAST(:minConfidence AS double precision)) " +
                   "AND i.lat IS NOT NULL AND i.lng IS NOT NULL " +
                   "GROUP BY ROUND(CAST(i.lat AS numeric), 3), ROUND(CAST(i.lng AS numeric), 3)",
           nativeQuery = true)
    List<Object[]> findHeatmapPoints(
        @Param("from") String from,
        @Param("to") String to,
        @Param("types") List<String> types,
        @Param("minConfidence") Double minConfidence);
}
