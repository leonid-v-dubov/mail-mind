package de.leonidu.mailmind.mail;

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
@Table(name = "last_processed_uid")
public class LastProcessedUidEntity {
    @Id
    @Column(name = "folder", nullable = false, length = 255)
    private String folder;
    @Setter
    @Column(name = "last_uid", nullable = false)
    private Long lastUid;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LastProcessedUidEntity() {
    }

    public LastProcessedUidEntity(String folder, Long lastUid) {
        this.folder = folder;
        this.lastUid = lastUid;
    }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}