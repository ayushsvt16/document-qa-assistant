package com.ayush.documentqa.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "message_sources")
public class MessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "similarity_score", nullable = false)
    private double similarityScore;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getChunkId() { return chunkId; }
    public void setChunkId(UUID chunkId) { this.chunkId = chunkId; }

    public double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(double similarityScore) { this.similarityScore = similarityScore; }
}
