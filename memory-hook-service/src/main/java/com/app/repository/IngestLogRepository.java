package com.app.repository;

import com.app.model.IngestLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngestLogRepository extends JpaRepository<IngestLog, String> {

  long countByStatusAndAtBetween(String status, Instant from, Instant to);

  @Modifying
  @Query("delete from IngestLog l where l.at < :before")
  int deleteOlderThan(@Param("before") Instant before);
}
