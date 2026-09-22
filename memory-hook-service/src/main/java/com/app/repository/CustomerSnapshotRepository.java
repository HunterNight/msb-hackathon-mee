package com.app.repository;

import com.app.model.CustomerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSnapshotRepository extends JpaRepository<CustomerSnapshot, String> {}
