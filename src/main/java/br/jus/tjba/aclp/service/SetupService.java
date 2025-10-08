package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.SetupAdminDTO;
import br.jus.tjba.aclp.model.SetupStatus;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.repository.SetupStatusRepository;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private final SetupStatusRepository setupStatusRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verifica se o setup é necessário
     */
    public boolean isSetupRequired() {
        boolean statusCompleted = setupStatusRepository.isCompleted();
        long userCount = usuarioRepository.count();

        // Setup é necessário se não foi concluído E não há usuários
        boolean setupRequired = !statusCompleted && userCount == 0;

        log.debug("Setup check - Status completed: {}, User count: {}, Setup required: {}",
                statusCompleted, userCount, setupRequired);

        return setupRequired;
    }

    /**
     * Retorna informações sobre o status do setup
     */
    public Map<String, Object> getSetupStatus() {
        SetupStatus status = setupStatusRepository.getSetupStatus();
        boolean setupRequired = isSetupRequired();

        return Map.of(
                "setupRequired", setupRequired,
                "setupCompleted", status.isCompleted(),
                "completedAt", status.getCompletedAt() != null ? status.getCompletedAt() : "",
                "firstAdminEmail", status.getFirstAdminEmail() != null ? status.getFirstAdminEmail() : "",
                "appName", "ACLP - Sistema TJBA",
                "version", status.getSetupVersion()
        );
    }

    /**
     * Cria o primeiro administrador do sistema
     */
    @Transactional
    public Usuario createFirstAdmin(SetupAdminDTO dto, String clientIp) {
        log.info("Iniciando criação do primeiro administrador - Email: {}, IP: {}", dto.getEmail(), clientIp);

        // VALIDAÇÃO CRÍTICA: Verificar se setup ainda é necessário
        if (!isSetupRequired()) {
            throw new IllegalStateException("Setup já foi concluído ou sistema já possui usuários!");
        }

        // Limpar e validar dados
        dto.limparEFormatarDados();
        validarDadosAdmin(dto);

        // Verificar duplicidade de email (extra segurança)
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email já está em uso no sistema");
        }

        try {
            // Criar primeiro administrador
            Usuario admin = Usuario.builder()
                    .nome(dto.getNome())
                    .email(dto.getEmail())
                    .senha(passwordEncoder.encode(dto.getSenha()))
                    .tipo(TipoUsuario.ADMIN)
                    .departamento(dto.getDepartamento() != null && !dto.getDepartamento().trim().isEmpty()
                            ? dto.getDepartamento() : "Administração do Sistema")
                    .ativo(true)
                    .build();

            Usuario adminSalvo = usuarioRepository.save(admin);

            // Marcar setup como concluído
            setupStatusRepository.markAsCompleted(adminSalvo.getEmail(), clientIp);

            // Log de auditoria crítico
            log.warn("🚨 SETUP CONCLUÍDO - Primeiro administrador criado");
            log.warn("   Nome: {}", adminSalvo.getNome());
            log.warn("   Email: {}", adminSalvo.getEmail());
            log.warn("   IP: {}", clientIp);
            log.warn("   Data/Hora: {}", LocalDateTime.now());
            log.warn("   ID: {}", adminSalvo.getId());

            return adminSalvo;

        } catch (Exception e) {
            log.error("Erro ao criar primeiro administrador", e);
            throw new RuntimeException("Erro interno ao criar administrador: " + e.getMessage());
        }
    }

    /**
     * Valida os dados do administrador
     */
    private void validarDadosAdmin(SetupAdminDTO dto) {
        // Validar email institucional
        if (!dto.getEmail().endsWith("@tjba.jus.br")) {
            throw new IllegalArgumentException("Email deve ser institucional (@tjba.jus.br)");
        }

        // Validar nome completo
        if (!dto.isNomeCompleto()) {
            throw new IllegalArgumentException("Informe nome completo (nome e sobrenome)");
        }

        // Validar força da senha
        if (!dto.isSenhaForte()) {
            throw new IllegalArgumentException(
                    "Senha deve conter pelo menos: 8 caracteres, 1 maiúscula, 1 minúscula, 1 número e 1 símbolo (@$!%*?&)");
        }

        // Validar confirmação de senha
        if (!dto.isSenhasCoincidentes()) {
            throw new IllegalArgumentException("Senhas não coincidem");
        }

        // Validar comprimento do nome
        if (dto.getNome().length() < 3) {
            throw new IllegalArgumentException("Nome deve ter pelo menos 3 caracteres");
        }

        // Validar formato do telefone se fornecido
        if (dto.getTelefone() != null && !dto.getTelefone().trim().isEmpty()) {
            if (!dto.getTelefone().matches("\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}")) {
                throw new IllegalArgumentException("Telefone deve ter formato válido (ex: (71) 99999-9999)");
            }
        }
    }

    /**
     * Força reset do setup (apenas para desenvolvimento)
     */
    @Transactional
    public void resetSetup() {
        log.warn("🚨 RESET DO SETUP SOLICITADO");

        // Em produção, esse método deveria ter proteções adicionais
        setupStatusRepository.resetSetup();

        log.warn("Setup resetado - sistema retornará ao estado inicial");
    }

    /**
     * Verifica se o sistema tem administradores
     */
    public boolean hasAdministrators() {
        return usuarioRepository.countByTipo(TipoUsuario.ADMIN) > 0;
    }

    /**
     * Informações para auditoria
     */
    public Map<String, Object> getSetupAuditInfo() {
        SetupStatus status = setupStatusRepository.getSetupStatus();

        return Map.of(
                "setupCompleted", status.isCompleted(),
                "completedAt", status.getCompletedAt() != null ? status.getCompletedAt() : "N/A",
                "firstAdminEmail", status.getFirstAdminEmail() != null ? status.getFirstAdminEmail() : "N/A",
                "completedByIp", status.getCompletedByIp() != null ? status.getCompletedByIp() : "N/A",
                "totalAdministrators", usuarioRepository.countByTipo(TipoUsuario.ADMIN),
                "totalUsers", usuarioRepository.count()
        );
    }
}