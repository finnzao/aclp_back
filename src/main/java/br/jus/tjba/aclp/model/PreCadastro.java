package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "token_convite", nullable = false)
    private String tokenConvite;

    @Column(name = "token_verificacao", unique = true, nullable = false)
    private String tokenVerificacao;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    @Column(length = 100)
    private String comarca;

    @Column(length = 100)
    private String departamento;

    @Column(length = 100)
    private String cargo;

    @Column(name = "ip_cadastro", length = 45)
    private String ipCadastro;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    @Builder.Default
    private Boolean verificado = false;

    @Column(name = "verificado_em")
    private LocalDateTime verificadoEm;

    @Column(name = "ip_verificacao", length = 45)
    private String ipVerificacao;

    @Column(name = "tentativas_verificacao")
    @Builder.Default
    private Integer tentativasVerificacao = 0;

    @Column(name = "usuario_criado_id")
    private Long usuarioCriadoId;

    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        }
        if (this.expiraEm == null) {
            this.expiraEm = br.jus.tjba.aclp.util.TempoAtual.agora().plusHours(24); // 24h para verificar
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

    public boolean isValido() {
        return !verificado && br.jus.tjba.aclp.util.TempoAtual.agora().isBefore(expiraEm);
    }

    public boolean isExpirado() {
        return br.jus.tjba.aclp.util.TempoAtual.agora().isAfter(expiraEm);
    }

    public boolean isVerificado() {
        return Boolean.TRUE.equals(verificado);
    }

    public void marcarVerificado(String ip, Long usuarioId) {
        this.verificado = true;
        this.verificadoEm = br.jus.tjba.aclp.util.TempoAtual.agora();
        this.ipVerificacao = ip;
        this.usuarioCriadoId = usuarioId;
    }

    public void incrementarTentativas() {
        if (this.tentativasVerificacao == null) {
            this.tentativasVerificacao = 0;
        }
        this.tentativasVerificacao++;
    }

    public boolean excedeuTentativas() {
        return this.tentativasVerificacao != null && this.tentativasVerificacao >= 5;
    }
}