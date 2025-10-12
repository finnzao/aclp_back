package br.jus.tjba.aclp.model;

import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "convites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Email do convidado - será o login dele no sistema
     * No novo fluxo pode ser nulo (preenchido depois)
     */
    @Column(nullable = true)
    private String email;

    /**
     * Tipo de usuário que será criado (USUARIO ou ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    /**
     * Token único para validação do convite
     * Gerado automaticamente via UUID
     */
    @Column(unique = true, nullable = false, length = 100)
    private String token;

    /**
     * Status atual do convite
     * PENDENTE: aguardando ativação
     * ATIVADO: convite foi usado e conta criada
     * EXPIRADO: passou do prazo de validade
     * CANCELADO: admin cancelou o convite
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConvite status;

    /**
     * Comarca copiada do admin que criou o convite
     * O usuário criado terá a mesma comarca
     */
    @Column(name = "comarca", length = 100)
    private String comarca;

    /**
     * Departamento copiado do admin que criou o convite
     * O usuário criado terá o mesmo departamento
     */
    @Column(name = "departamento", length = 100)
    private String departamento;

    /**
     * Admin que criou este convite
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    /**
     * Usuário criado quando o convite foi ativado
     * null enquanto convite está pendente
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * IP de onde o convite foi criado (auditoria)
     */
    @Column(name = "ip_criacao", length = 45)
    private String ipCriacao;

    /**
     * IP de onde o convite foi ativado
     */
    @Column(name = "ip_ativacao", length = 45)
    private String ipAtivacao;

    /**
     * Quantidade de vezes que o link pode ser usado (novo campo)
     */
    @Column(name = "quantidade_usos")
    @Builder.Default
    private Integer quantidadeUsos = 1;

    /**
     * Quantidade de vezes que o link já foi usado (novo campo)
     */
    @Column(name = "usos_realizados")
    @Builder.Default
    private Integer usosRealizados = 0;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;

    /**
     * Inicializa valores padrão
     */
    @PrePersist
    protected void onCreate() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
        if (this.expiraEm == null) {
            this.expiraEm = LocalDateTime.now().plusDays(7); // 7 dias de validade
        }
        if (this.token == null) {
            this.token = UUID.randomUUID().toString();
        }
        if (this.status == null) {
            this.status = StatusConvite.PENDENTE;
        }
        if (this.quantidadeUsos == null) {
            this.quantidadeUsos = 1;
        }
        if (this.usosRealizados == null) {
            this.usosRealizados = 0;
        }
    }

    /**
     * Verifica se o convite pode ser usado
     * @return true se está pendente e não expirou
     */
    public boolean isValido() {
        return status == StatusConvite.PENDENTE &&
                !isExpirado() &&
                (quantidadeUsos == null || usosRealizados < quantidadeUsos);
    }

    /**
     * Verifica se o convite já expirou
     * @return true se a data atual passou da data de expiração
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Ativa o convite após criação do usuário
     * @param usuario Usuário criado
     * @param ip IP de onde foi ativado
     */
    public void ativar(Usuario usuario, String ip) {
        this.usuario = usuario;
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = LocalDateTime.now();
        this.ipAtivacao = ip;
    }

    /**
     * Registra uso do convite (para convites com múltiplos usos)
     */
    public void registrarUso() {
        if (this.usosRealizados == null) {
            this.usosRealizados = 0;
        }
        this.usosRealizados++;

        // SEMPRE marca como ativado após o primeiro uso
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = LocalDateTime.now();
    }

    /**
     * Retorna quantos usos ainda estão disponíveis
     */
    public int getUsosRestantes() {
        if (quantidadeUsos == null) {
            return Integer.MAX_VALUE; // Ilimitado
        }
        if (usosRealizados == null) {
            return quantidadeUsos;
        }
        return Math.max(0, quantidadeUsos - usosRealizados);
    }

    /**
     * Cancela o convite (admin pode cancelar antes de ser usado)
     */
    public void cancelar() {
        this.status = StatusConvite.CANCELADO;
    }

    /**
     * Marca o convite como expirado (job automático)
     */
    public void expirar() {
        this.status = StatusConvite.EXPIRADO;
    }
}