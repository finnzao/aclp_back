package br.jus.tjba.aclp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts",
        indexes = {
                @Index(name = "idx_attempt_email", columnList = "email"),
                @Index(name = "idx_attempt_ip", columnList = "ip_address"),
                @Index(name = "idx_attempt_time", columnList = "attempt_time")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "attempt_time", nullable = false)
    private LocalDateTime attemptTime;

    @Column(length = 100)
    private String location; // Geolocalização aproximada

    @Column(length = 100)
    private String device; // Tipo de dispositivo

    @Column(name = "suspicious", nullable = false)
    @Builder.Default
    private Boolean suspicious = false;

    @Column(name = "blocked", nullable = false)
    @Builder.Default
    private Boolean blocked = false;
}
