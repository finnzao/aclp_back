package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade para armazenar pré-cadastros aguardando verificação de email
 */
@Entity
@Table(name = "pre_cadastros",
        indexes = {
                @Index(name = "idx_pre_cadastro_email", columnList = "email"),
                @Index(name = "idx_pre_cadastro_token_verificacao", columnList = "token_verificacao"),
                @Index(name = "idx_pre_cadastro_token_convite", columnList = "token_convite")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Token do convite que originou este pré-cadastro
     */
    @Column(name = "token_convite", nullable = false)
    private String tokenConvite;

    /**
     * Token único para verificação de email
     */
    @Column(name = "token_verificacao", unique = true, nullable = false)
    private String tokenVerificacao;

    /**
     * Email do usuário (será o login)
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Nome completo do usuário
     */
    @Column(nullable = false)
    private String nome;

    /**
     * Senha já criptografada
     */
    @Column(nullable = false)
    private String senha;

    /**
     * Tipo de usuário que será criado
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    /**
     * Comarca herdada do convite
     */
    @Column(length = 100)
    private String comarca;

    /**
     * Departamento herdado do convite
     */
    @Column(length = 100)
    private String departamento;

    /**
     * Cargo informado pelo usuário
     */
    @Column(length = 100)
    private String cargo;

    /**
     * IP de onde foi feito o pré-cadastro
     */
    @Column(name = "ip_cadastro", length = 45)
    private String ipCadastro;

    /**
     * Data/hora do pré-cadastro
     */
    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    /**
     * Data/hora de expiração para verificação (24h)
     */
    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    /**
     * Se o email foi verificado
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean verificado = false;

    /**
     * Data/hora da verificação
     */
    @Column(name = "verificado_em")
    private LocalDateTime verificadoEm;

    /**
     * IP de onde foi verificado
     */
    @Column(name = "ip_verificacao", length = 45)
    private String ipVerificacao;

    /**
     * Tentativas de verificação (para rate limiting)
     */
    @Column(name = "tentativas_verificacao")
    @Builder.Default
    private Integer tentativasVerificacao = 0;

    /**
     * ID do usuário criado após verificação
     */
    @Column(name = "usuario_criado_id")
    private Long usuarioCriadoId;

    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.expiraEm == null) {
            this.expiraEm = LocalDateTime.now().plusHours(24); // 24h para verificar
        }
        if (this.tokenVerificacao == null) {
            this.tokenVerificacao = "ver-" + UUID.randomUUID().toString();
        }
        if (this.verificado == null) {
            this.verificado = false;
        }
        if (this.tentativasVerificacao == null) {
            this.tentativasVerificacao = 0;
        }
    }

    /**
     * Verifica se o pré-cadastro ainda é válido
     */
    public boolean isValido() {
        return !verificado && LocalDateTime.now().isBefore(expiraEm);
    }

    /**
     * Verifica se expirou
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Verifica se foi verificado
     */
    public boolean isVerificado() {
        return Boolean.TRUE.equals(verificado);
    }

    /**
     * Marca como verificado
     */
    public void marcarVerificado(String ip, Long usuarioId) {
        this.verificado = true;
        this.verificadoEm = LocalDateTime.now();
        this.ipVerificacao = ip;
        this.usuarioCriadoId = usuarioId;
    }

    /**
     * Incrementa tentativas de verificação
     */
    public void incrementarTentativas() {
        if (this.tentativasVerificacao == null) {
            this.tentativasVerificacao = 0;
        }
        this.tentativasVerificacao++;
    }

    /**
     * Verifica se excedeu tentativas (máximo 5)
     */
    public boolean excedeuTentativas() {
        return this.tentativasVerificacao != null && this.tentativasVerificacao >= 5;
    }
}