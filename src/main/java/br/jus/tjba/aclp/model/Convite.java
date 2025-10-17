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
@Table(name = "convites", indexes = {
        @Index(name = "idx_convites_token", columnList = "token"),
        @Index(name = "idx_convites_status", columnList = "status"),
        @Index(name = "idx_convites_email", columnList = "email"),
        @Index(name = "idx_convites_expira_status", columnList = "expira_em, status")
})
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
     * PODE SER NULL para convites genéricos/reutilizáveis
     * Quando NULL, o usuário deverá informar o email ao ativar o convite
     */
    @Column(nullable = true, length = 255)
    private String email;

    /**
     * Tipo de usuário que será criado (USUARIO ou ADMIN)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
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
    @Column(nullable = false, length = 20)
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
     * Quantidade de vezes que o link pode ser usado
     * SEMPRE 1 = uso único (não permite reutilização)
     */
    @Column(name = "quantidade_usos")
    @Builder.Default
    private Integer quantidadeUsos = 1;

    /**
     * Quantidade de vezes que o link já foi usado
     * 0 = não usado, 1 = já usado
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
     * Verifica se é um convite genérico (sem email específico)
     */
    public boolean isGenerico() {
        return this.email == null || this.email.trim().isEmpty();
    }

    /**
     * Verifica se o convite pode ser usado
     */
    public boolean isValido() {
        return status == StatusConvite.PENDENTE &&
                !isExpirado() &&
                usosRealizados == 0; // Só válido se nunca foi usado
    }

    /**
     * Verifica se o convite já expirou
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(expiraEm);
    }

    /**
     * Verifica se ainda há usos disponíveis (sempre 1 uso)
     */
    public boolean temUsosDisponiveis() {
        return usosRealizados == 0;
    }

    /**
     * Retorna quantos usos ainda estão disponíveis (0 ou 1)
     */
    public int getUsosRestantes() {
        return usosRealizados == 0 ? 1 : 0;
    }

    /**
     * Registra uso do convite (marca como usado)
     */
    public void registrarUso() {
        this.usosRealizados = 1;
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = LocalDateTime.now();
    }

    /**
     * Ativa o convite após criação do usuário (uso único)
     * @param usuario Usuário criado
     * @param ip IP de onde foi ativado
     */
    public void ativar(Usuario usuario, String ip) {
        this.usuario = usuario;
        this.status = StatusConvite.ATIVADO;
        this.ativadoEm = LocalDateTime.now();
        this.ipAtivacao = ip;
        this.usosRealizados = 1;
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

    /**
     * Valida se o email pode ser usado com este convite
     */
    public boolean validarEmail(String emailParaValidar) {
        if (emailParaValidar == null || emailParaValidar.trim().isEmpty()) {
            return false;
        }

        // Se convite tem email específico, deve coincidir
        if (!isGenerico()) {
            return this.email.equalsIgnoreCase(emailParaValidar.trim());
        }

        // Se é genérico, qualquer email válido é aceito
        return true;
    }

    /**
     * Retorna descrição do tipo de convite
     */
    public String getTipoConviteDescricao() {
        if (isGenerico()) {
            return "Link Genérico (Uso Único)";
        } else {
            return "Convite Específico (Uso Único)";
        }
    }
}