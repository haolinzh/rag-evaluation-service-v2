package com.rag.eval.repository;

import com.rag.eval.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepo extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findBySessionIdAndOwnerIdOrderByCreatedAtAsc(String sessionId, Long ownerId);

    void deleteBySessionIdAndOwnerId(String sessionId, Long ownerId);
}
