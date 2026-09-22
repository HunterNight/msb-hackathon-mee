package com.app.repository;

import com.app.model.CustomerAutonomy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAutonomyRepository extends JpaRepository<CustomerAutonomy, UUID> {}
