package com.app.repository;

import com.app.model.DlqEntry;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DlqEntryRepository extends JpaRepository<DlqEntry, UUID> {

  Page<DlqEntry> findAllByOrderByAtDesc(Pageable pageable);

  long countByAtBetween(Instant from, Instant to);
}
