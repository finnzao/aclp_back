package br.jus.tjba.aclp.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "setup_status")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupStatus {

    @Id
    private String id = "SINGLE_ROW";

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = Boolean.FALSE;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by_ip", length = 45)
    private String completedByIp;

    @Column(name = "first_admin_email", length = 150)
    private String firstAdminEmail;

    @Column(name = "setup_version", length = 10)
    @Builder.Default
    private String setupVersion = "1.0";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.trim().isEmpty()) {
            this.id = "SINGLE_ROW";
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.completed == null) {
            this.completed = Boolean.FALSE;
        }
    }

    public boolean isCompleted() {
        return Boolean.TRUE.equals(completed);
    }

    public void markAsCompleted(String adminEmail, String clientIp) {
        this.completed = Boolean.TRUE;
        this.completedAt = LocalDateTime.now();
        this.firstAdminEmail = adminEmail;
        this.completedByIp = clientIp;
    }
}