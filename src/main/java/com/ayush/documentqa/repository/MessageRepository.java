package com.ayush.documentqa.repository;

import com.ayush.documentqa.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Returns messages ordered by creation time (oldest first) */
    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /** Returns the N most recent messages for conversation history (newest first, reverse in service) */
    List<Message> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
