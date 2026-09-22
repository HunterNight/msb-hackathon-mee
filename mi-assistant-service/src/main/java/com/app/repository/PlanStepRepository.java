package com.app.repository;

import com.app.model.PlanStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanStepRepository extends JpaRepository<PlanStep, UUID> {

  List<PlanStep> findByPlanIdOrderBySeqAsc(UUID planId);

  List<PlanStep> findByWaitingFor(String waitingFor);
}
