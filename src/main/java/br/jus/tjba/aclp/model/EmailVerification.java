package br.jus.tjba.aclp.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidade para armazenar códigos de verificação por email
 * Usado para verificar emails antes de criar usuários
 */
@Entity
@Table(name = "email_verification",
        indexes = {
                @Index(name = "idx_email_verification_email", columnList = "email"),
                @Index(name = "idx_email_verification_codigo", columnList = "codigo"),
                @Index(name = "idx_email_verification_expira", columnList = "expira_em"),
                @Index(name = "idx_email_verification_verificado", columnList = "verificado")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "verificado", nullable = false)
    @Builder.Default
    private Boolean verificado = Boolean.FALSE;

    @Column(name = "tentativas", nullable = false)
    @Builder.Default
    private Integer tentativas = 0;

    @Column(name = "max_tentativas", nullable = false)
    @Builder.Default
    private Integer maxTentativas = 5;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "verificado_em")
    private LocalDateTime verificadoEm;

    @Column(name = "ip_solicitacao", length = 45)
    private String ipSolicitacao;

    @Column(name = "ip_verificacao", length = 45)
    private String ipVerificacao;

    @Column(name = "tipo_usuario", length = 20)
    private String tipoUsuario; // "ADMIN" ou "USUARIO"

    @Column(name = "dados_usuario", columnDefinition = "TEXT")
    private String dadosUsuario; // JSON temporário com dados do usuário

    @Version
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.verificado == null) {
            this.verificado = Boolean.FALSE;
        }
        if (this.tentativas == null) {
            this.tentativas = 0;
        }
        if (this.maxTentativas == null) {
            this.maxTentativas = 5;
        }
        if (this.version == null) {
            this.version = 0L;
        }

        // Se não foi definido prazo de expiração, definir para 10 minutos
        if (this.expiraEm == null) {
            this.expiraEm = this.criadoEm.plusMinutes(10);
        }
    }

    /**
     * Verifica se o código está expirado
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Verifica se ainda pode tentar verificar
     */
    public boolean podeTentar() {
        return tentativas < maxTentativas && !isExpirado() && !verificado;
    }

    /**
     * Incrementa tentativas de verificação
     */
    public void incrementarTentativas() {
        this.tentativas++;
    }

    /**
     * Marca como verificado
     */
    public void marcarComoVerificado(String ipVerificacao) {
        this.verificado = Boolean.TRUE;
        this.verificadoEm = LocalDateTime.now();
        this.ipVerificacao = ipVerificacao;
    }

    /**
     * Verifica se o código está válido para uso
     */
    public boolean isValido() {
        return !isExpirado() && !verificado && podeTentar();
    }

    /**
     * Calcula tempo restante em minutos
     */
    public long getMinutosRestantes() {
        if (isExpirado()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiraEm).toMinutes();
    }

    /**
     * Retorna quantas tentativas restam
     */
    public int getTentativasRestantes() {
        return Math.max(0, maxTentativas - tentativas);
    }
}