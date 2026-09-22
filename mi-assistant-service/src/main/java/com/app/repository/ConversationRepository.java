package com.app.repository;

import com.app.model.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  /** Anti-IDOR: a conversation is only ever reachable through its owner (guideline 05 §4). */
  Optional<Conversation> findByIdAndCustomerId(UUID id, UUID customerId);

  Optional<Conversation> findFirstByCustomerIdAndStatusOrderByLastMessageAtDesc(
      UUID customerId, String status);

  Page<Conversation> findByCustomerIdOrderByLastMessageAtDesc(UUID customerId, Pageable pageable);
}
