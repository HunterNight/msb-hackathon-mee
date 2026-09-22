package com.app.repository;

import com.app.model.ErasureRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErasureRequestRepository extends JpaRepository<ErasureRequest, UUID> {

  Optional<ErasureRequest> findByIdAndPseudoId(UUID id, String pseudoId);

  List<ErasureRequest> findByStatusIn(List<String> statuses);

  boolean existsByPseudoIdAndStatusIn(String pseudoId, List<String> statuses);
}
