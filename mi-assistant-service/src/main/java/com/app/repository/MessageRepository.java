package com.app.repository;

import com.app.model.Message;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  List<Message> findByConversationIdOrderBySeqAsc(UUID conversationId);

  List<Message> findByConversationIdOrderBySeqDesc(UUID conversationId, Pageable pageable);

  Optional<Message> findByIdAndCustomerId(UUID id, UUID customerId);

  @Query("select coalesce(max(m.seq), 0) from Message m where m.conversationId = :conversationId")
  int maxSeq(@Param("conversationId") UUID conversationId);
}
