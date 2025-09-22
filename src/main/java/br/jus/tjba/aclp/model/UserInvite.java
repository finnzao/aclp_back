package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade para gerenciar convites de usuários
 * Armazena tokens temporários para primeiro acesso
 */
@Entity
@Table(name = "user_invites",
        indexes = {
                @Index(name = "idx_invite_token", columnList = "token", unique = true),
                @Index(name = "idx_invite_email", columnList = "email"),
                @Index(name = "idx_invite_status", columnList = "status"),
                @Index(name = "idx_invite_expira", columnList = "expira_em")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, unique = true, length = 100)
    private String token;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
    private TipoUsuario tipoUsuario;

    @Column(name = "departamento", length = 100)
    private String departamento;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "escopo", length = 200)
    private String escopo; // Unidade/Lotação

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, ACCEPTED, EXPIRED, CANCELLED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor; // Admin que criou o convite

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "aceito_em")
    private LocalDateTime aceitoEm;

    @Column(name = "ip_criacao", length = 45)
    private String ipCriacao;

    @Column(name = "ip_aceite", length = 45)
    private String ipAceite;

    @Column(name = "tentativas_acesso")
    @Builder.Default
    private Integer tentativasAcesso = 0;

    @Column(name = "max_tentativas")
    @Builder.Default
    private Integer maxTentativas = 5;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    // Métodos utilitários

    @PrePersist
    public void prePersist() {
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.expiraEm == null) {
            this.expiraEm = criadoEm.plusHours(72); // 72 horas por padrão
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm) || "EXPIRED".equals(status);
    }

    public boolean isPendente() {
        return "PENDING".equals(status) && !isExpirado();
    }

    public boolean isAceito() {
        return "ACCEPTED".equals(status);
    }

    public boolean podeTentar() {
        return tentativasAcesso < maxTentativas && isPendente();
    }

    public void incrementarTentativas() {
        this.tentativasAcesso++;
    }

    public void marcarComoAceito(String ipAceite) {
        this.status = "ACCEPTED";
        this.aceitoEm = LocalDateTime.now();
        this.ipAceite = ipAceite;
    }

    public void marcarComoExpirado() {
        this.status = "EXPIRED";
    }

    public void cancelar() {
        this.status = "CANCELLED";
    }

    public long getHorasRestantes() {
        if (isExpirado()) return 0;
        return java.time.Duration.between(LocalDateTime.now(), expiraEm).toHours();
    }
}