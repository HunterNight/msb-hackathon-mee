package com.app.repository;

import com.app.model.RecordSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordSourceRepository extends JpaRepository<RecordSource, UUID> {

  List<RecordSource> findByRecordId(UUID recordId);
}
