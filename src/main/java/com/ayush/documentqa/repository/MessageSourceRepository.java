package com.ayush.documentqa.repository;

import com.ayush.documentqa.entity.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageSourceRepository extends JpaRepository<MessageSource, UUID> {

    List<MessageSource> findByMessageId(UUID messageId);
}
