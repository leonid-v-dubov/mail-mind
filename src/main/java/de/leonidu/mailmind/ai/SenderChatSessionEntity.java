package de.leonidu.mailmind.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "sender_chat_session")
public class SenderChatSessionEntity {
    @Id
    @Column(name = "sender_email", nullable = false, length = 320)
    private String senderEmail;
    @Setter
    @Column(name = "last_response_id", nullable = false, length = 128)
    private String lastResponseId;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SenderChatSessionEntity() {
    }

    public SenderChatSessionEntity(String senderEmail, String lastResponseId) {
        this.senderEmail = senderEmail;
        this.lastResponseId = lastResponseId;
    }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

}

