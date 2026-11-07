package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsSourceRepository extends JpaRepository<NewsSource, Long> {
    List<NewsSource> findByActiveTrue();
    List<NewsSource> findByType(SourceType type);
    List<NewsSource> findByActiveTrueAndType(SourceType type);
}
