package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import br.jus.tjba.aclp.model.enums.StatusUsuario;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "usuarios",
        indexes = {
                @Index(name = "idx_usuario_email", columnList = "email"),
                @Index(name = "idx_usuario_tipo", columnList = "tipo"),
                @Index(name = "idx_usuario_ativo", columnList = "ativo")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email deve ser válido")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "Senha é obrigatória")
    @Column(name = "senha", nullable = false)
    private String senha;

    @NotNull(message = "Tipo é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    @Builder.Default
    private TipoUsuario tipo = TipoUsuario.USUARIO;

    @Size(max = 100, message = "Departamento deve ter no máximo 100 caracteres")
    @Column(name = "departamento", length = 100)
    private String departamento;

    @Column(name = "comarca", length = 100)
    private String comarca;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private Boolean ativo = Boolean.TRUE;

    @Column(name = "ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "configuracoes", columnDefinition = "TEXT")
    private String configuracoes;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    //StatusUsuario
    @Enumerated(EnumType.STRING)
    @Column(name = "status_usuario", nullable = false, length = 20)
    @Builder.Default
    private StatusUsuario statusUsuario = StatusUsuario.ACTIVE;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 100)
    private String mfaSecret;

    @Column(name = "email_verificado", nullable = false)
    @Builder.Default
    private Boolean emailVerificado = false;

    @Column(name = "data_verificacao_email")
    private LocalDateTime dataVerificacaoEmail;

    @Column(name = "tentativas_login_falhadas")
    @Builder.Default
    private Integer tentativasLoginFalhadas = 0;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(name = "deve_trocar_senha", nullable = false)
    @Builder.Default
    private Boolean deveTrocarSenha = false;

    @Column(name = "senha_expira_em")
    private LocalDateTime senhaExpiraEm;

    @Column(name = "ultimo_reset_senha")
    private LocalDateTime ultimoResetSenha;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expiry")
    private LocalDateTime passwordResetExpiry;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.ativo == null) {
            this.ativo = Boolean.TRUE;
        }
        if (this.tipo == null) {
            this.tipo = TipoUsuario.USUARIO;
        }
        if (this.statusUsuario == null) {
            this.statusUsuario = StatusUsuario.ACTIVE;
        }
        if (this.mfaEnabled == null) {
            this.mfaEnabled = Boolean.FALSE;
        }
        if (this.emailVerificado == null) {
            this.emailVerificado = Boolean.FALSE;
        }
        if (this.deveTrocarSenha == null) {
            this.deveTrocarSenha = Boolean.FALSE;
        }
        if (this.version == null) {
            this.version = 0L;
        }

        if (this.senhaExpiraEm == null) {
            this.senhaExpiraEm = LocalDateTime.now().plusDays(320);
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public boolean isAdmin() {
        return tipo != null && tipo.isAdmin();
    }

    public boolean isUsuario() {
        return tipo != null && tipo.isUsuario();
    }

    public void registrarLogin() {
        this.ultimoLogin = LocalDateTime.now();
    }

    public String getNomeCompleto() {
        return nome + (departamento != null ? " (" + departamento + ")" : "");
    }

    /**
     * Verifica se usuário pode fazer login
     */
    public boolean podeLogar() {
        if (statusUsuario != StatusUsuario.ACTIVE) {
            return false;
        }
        if (!ativo) {
            return false;
        }
        if (bloqueadoAte != null && bloqueadoAte.isAfter(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    /**
     * Verifica se senha expirou
     */
    public boolean senhaExpirada() {
        return senhaExpiraEm != null && senhaExpiraEm.isBefore(LocalDateTime.now());
    }

    /**
     * Incrementa tentativas de login falhadas
     */
    public void incrementarTentativasFalhadas() {
        if (tentativasLoginFalhadas == null) {
            tentativasLoginFalhadas = 0;
        }
        tentativasLoginFalhadas++;

        // Bloquear após 5 tentativas
        if (tentativasLoginFalhadas >= 5) {
            bloqueadoAte = LocalDateTime.now().plusMinutes(30);
        }
    }

    /**
     * Reseta tentativas de login
     */
    public void resetarTentativas() {
        tentativasLoginFalhadas = 0;
        bloqueadoAte = null;
    }

    /**
     * Marca email como verificado
     */
    public void marcarEmailVerificado() {
        this.emailVerificado = true;
        this.dataVerificacaoEmail = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(email, usuario.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email);
    }
}