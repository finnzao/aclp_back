package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.model.enums.StatusConvite;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de Convite para novos usuários
 * Permite que admins convidem usuários por email
 */
@Entity
@Table(name = "convites",
        indexes = {
                @Index(name = "idx_convite_token", columnList = "token"),
                @Index(name = "idx_convite_email", columnList = "email"),
                @Index(name = "idx_convite_status", columnList = "status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Token é obrigatório")
    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Column(nullable = false, length = 150)
    private String email;

    @NotNull(message = "Tipo de usuário é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
    private TipoUsuario tipoUsuario;

    @NotNull(message = "Status é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusConvite status = StatusConvite.PENDENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "expira_em", nullable = false)
    @Builder.Default
    private LocalDateTime expiraEm = LocalDateTime.now().plusDays(7);

    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "ip_criacao", length = 50)
    private String ipCriacao;

    @Column(name = "ip_ativacao", length = 50)
    private String ipAtivacao;

    @PrePersist
    public void prePersist() {
        if (this.token == null || this.token.isEmpty()) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.expiraEm == null) {
            this.expiraEm = LocalDateTime.now().plusDays(7);
        }
        if (this.status == null) {
            this.status = StatusConvite.PENDENTE;
        }
    }

    /**
     * Verifica se o convite está expirado
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Verifica se o convite é válido (pendente e não expirado)
     */
    public boolean isValido() {
        return status == StatusConvite.PENDENTE && !isExpirado();
    }

    /**
     * Marca o convite como ativado
     */
    public void ativar(Usuario usuario, String ipAtivacao) {
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = LocalDateTime.now();
        this.usuario = usuario;
        this.ipAtivacao = ipAtivacao;
    }

    /**
     * Cancela o convite
     */
    public void cancelar() {
        this.status = StatusConvite.CANCELADO;
    }

    /**
     * Marca como expirado
     */
    public void expirar() {
        this.status = StatusConvite.EXPIRADO;
    }
}