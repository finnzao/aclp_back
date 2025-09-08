package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.EmailVerificationDTO.*;
import br.jus.tjba.aclp.model.EmailVerification;
import br.jus.tjba.aclp.repository.EmailVerificationRepository;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Serviço responsável pela verificação de email com códigos de segurança
 * Gerencia todo o fluxo: solicitação → envio → verificação → token
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService; // Será implementado na próxima resposta

    private final SecureRandom secureRandom = new SecureRandom();
    private static final int CODIGO_LENGTH = 6;
    private static final int VALIDADE_MINUTOS = 10;
    private static final int MAX_TENTATIVAS = 5;
    private static final int MAX_CODIGOS_POR_HORA_IP = 10;
    private static final int MAX_CODIGOS_POR_HORA_EMAIL = 3;

    /**
     * Solicita um novo código de verificação
     */
    @Transactional
    public SolicitarCodigoResponseDTO solicitarCodigo(SolicitarCodigoDTO dto, String clientIp) {
        log.info("Solicitando código de verificação - Email: {}, Tipo: {}, IP: {}",
                dto.getEmail(), dto.getTipoUsuario(), clientIp);

        // Limpar e validar dados
        dto.limparEFormatarDados();
        validarSolicitacaoCodigo(dto, clientIp);

        // Invalidar códigos anteriores do mesmo email
        invalidarCodigosAnteriores(dto.getEmail());

        // Gerar novo código
        String codigo = gerarCodigo();
        LocalDateTime expiraEm = LocalDateTime.now().plusMinutes(VALIDADE_MINUTOS);

        // Criar registro de verificação
        EmailVerification verification = EmailVerification.builder()
                .email(dto.getEmail())
                .codigo(codigo)
                .expiraEm(expiraEm)
                .ipSolicitacao(clientIp)
                .tipoUsuario(dto.getTipoUsuario())
                .maxTentativas(MAX_TENTATIVAS)
                .build();

        EmailVerification saved = emailVerificationRepository.save(verification);

        // Enviar email com código
        enviarEmailComCodigo(dto.getEmail(), codigo, dto.getTipoUsuario());

        log.info("Código de verificação gerado - Email: {}, ID: {}, Expira em: {}",
                dto.getEmail(), saved.getId(), expiraEm);

        return SolicitarCodigoResponseDTO.builder()
                .status("success")
                .message("Código de verificação enviado para " + mascarEmail(dto.getEmail()))
                .email(mascarEmail(dto.getEmail()))
                .validadePorMinutos(VALIDADE_MINUTOS)
                .tentativasPermitidas(MAX_TENTATIVAS)
                .codigoId(gerarHashPublico(saved.getId()))
                .build();
    }

    /**
     * Verifica um código enviado pelo usuário
     */
    @Transactional
    public VerificarCodigoResponseDTO verificarCodigo(VerificarCodigoDTO dto, String clientIp) {
        log.info("Verificando código - Email: {}, IP: {}", dto.getEmail(), clientIp);

        // Limpar dados
        dto.limparEFormatarDados();

        // Buscar código ativo
        Optional<EmailVerification> optVerification = emailVerificationRepository
                .findByEmailAndCodigo(dto.getEmail(), dto.getCodigo(), LocalDateTime.now());

        if (optVerification.isEmpty()) {
            log.warn("Código inválido ou expirado - Email: {}, Código: {}, IP: {}",
                    dto.getEmail(), dto.getCodigo(), clientIp);

            // Verificar se existe código ativo para incrementar tentativas
            emailVerificationRepository.findActiveByEmail(dto.getEmail(), LocalDateTime.now())
                    .ifPresent(verification -> {
                        verification.incrementarTentativas();
                        emailVerificationRepository.save(verification);
                    });

            return VerificarCodigoResponseDTO.builder()
                    .status("error")
                    .message("Código inválido ou expirado")
                    .email(mascarEmail(dto.getEmail()))
                    .verificado(false)
                    .build();
        }

        EmailVerification verification = optVerification.get();

        // Verificar se ainda pode tentar
        if (!verification.podeTentar()) {
            log.warn("Muitas tentativas para código - Email: {}, Tentativas: {}, IP: {}",
                    dto.getEmail(), verification.getTentativas(), clientIp);

            return VerificarCodigoResponseDTO.builder()
                    .status("error")
                    .message("Muitas tentativas. Solicite um novo código.")
                    .email(mascarEmail(dto.getEmail()))
                    .verificado(false)
                    .tentativasRestantes(0)
                    .build();
        }

        // Código correto! Marcar como verificado
        verification.marcarComoVerificado(clientIp);
        emailVerificationRepository.save(verification);

        // Gerar token temporário para criação do usuário
        String tokenVerificacao = gerarTokenVerificacao(verification);

        log.info("Código verificado com sucesso - Email: {}, IP: {}", dto.getEmail(), clientIp);

        return VerificarCodigoResponseDTO.builder()
                .status("success")
                .message("Email verificado com sucesso")
                .email(mascarEmail(dto.getEmail()))
                .verificado(true)
                .tokenVerificacao(tokenVerificacao)
                .validoAte(LocalDateTime.now().plusMinutes(30).format(DateTimeFormatter.ofPattern("HH:mm")))
                .build();
    }

    /**
     * Consulta status de verificação sem fazer nova verificação
     */
    @Transactional(readOnly = true)
    public StatusVerificacaoDTO consultarStatus(String email) {
        log.debug("Consultando status de verificação - Email: {}", email);

        email = email.trim().toLowerCase();

        Optional<EmailVerification> optActive = emailVerificationRepository
                .findActiveByEmail(email, LocalDateTime.now());

        if (optActive.isEmpty()) {
            return StatusVerificacaoDTO.builder()
                    .email(mascarEmail(email))
                    .possuiCodigoAtivo(false)
                    .verificado(false)
                    .podeReenviar(true)
                    .build();
        }

        EmailVerification verification = optActive.get();

        return StatusVerificacaoDTO.builder()
                .email(mascarEmail(email))
                .possuiCodigoAtivo(true)
                .verificado(verification.getVerificado())
                .tentativasRestantes(verification.getTentativasRestantes())
                .minutosRestantes((int) verification.getMinutosRestantes())
                .podeReenviar(verification.getTentativas() >= MAX_TENTATIVAS || verification.isExpirado())
                .build();
    }

    /**
     * Reenvia código (invalida anterior e cria novo)
     */
    @Transactional
    public SolicitarCodigoResponseDTO reenviarCodigo(ReenviarCodigoDTO dto, String clientIp) {
        log.info("Reenviando código - Email: {}, IP: {}", dto.getEmail(), clientIp);

        dto.limparEFormatarDados();

        // Verificar rate limiting
        validarRateLimiting(dto.getEmail(), clientIp);

        // Buscar verificação anterior para manter tipo de usuário
        String tipoUsuario = emailVerificationRepository
                .findActiveByEmail(dto.getEmail(), LocalDateTime.now())
                .map(EmailVerification::getTipoUsuario)
                .orElse("USUARIO");

        // Criar nova solicitação
        SolicitarCodigoDTO novasolicitacao = SolicitarCodigoDTO.builder()
                .email(dto.getEmail())
                .tipoUsuario(tipoUsuario)
                .build();

        return solicitarCodigo(novasolicitacao, clientIp);
    }

    /**
     * Valida token de verificação (usado ao criar usuário)
     */
    public boolean validarTokenVerificacao(String email, String token) {
        log.debug("Validando token de verificação - Email: {}", email);

        // Buscar último código verificado
        Optional<EmailVerification> optVerification = emailVerificationRepository
                .findLastVerifiedByEmail(email.trim().toLowerCase());

        if (optVerification.isEmpty()) {
            log.warn("Nenhuma verificação encontrada para email: {}", email);
            return false;
        }

        EmailVerification verification = optVerification.get();

        // Token deve ter sido gerado nos últimos 30 minutos
        if (verification.getVerificadoEm().isBefore(LocalDateTime.now().minusMinutes(30))) {
            log.warn("Token expirado para email: {}", email);
            return false;
        }

        // Validar token
        String tokenEsperado = gerarTokenVerificacao(verification);
        boolean tokenValido = tokenEsperado.equals(token);

        if (!tokenValido) {
            log.warn("Token inválido para email: {}", email);
        }

        return tokenValido;
    }

    // ========== MÉTODOS PRIVADOS ==========

    private void validarSolicitacaoCodigo(SolicitarCodigoDTO dto, String clientIp) {
        // Validar email para tipo de usuário
        if (!dto.isEmailValidoParaTipo()) {
            if ("ADMIN".equals(dto.getTipoUsuario())) {
                throw new IllegalArgumentException("Administradores devem usar email institucional (@tjba.jus.br)");
            } else {
                throw new IllegalArgumentException("Email inválido");
            }
        }

        // Verificar se email já está em uso
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email já está cadastrado no sistema");
        }

        // Verificar rate limiting
        validarRateLimiting(dto.getEmail(), clientIp);
    }

    private void validarRateLimiting(String email, String clientIp) {
        LocalDateTime umaHoraAtras = LocalDateTime.now().minusHours(1);

        // Verificar limite por IP
        long codigosPorIp = emailVerificationRepository
                .countByIpSolicitacaoAndCriadoEmAfter(clientIp, umaHoraAtras);

        if (codigosPorIp >= MAX_CODIGOS_POR_HORA_IP) {
            throw new IllegalArgumentException("Muitas solicitações deste IP. Tente novamente mais tarde.");
        }

        // Verificar limite por email
        long codigosPorEmail = emailVerificationRepository
                .countByEmailAndCriadoEmAfter(email, umaHoraAtras);

        if (codigosPorEmail >= MAX_CODIGOS_POR_HORA_EMAIL) {
            throw new IllegalArgumentException("Muitas solicitações para este email. Tente novamente mais tarde.");
        }
    }

    private void invalidarCodigosAnteriores(String email) {
        emailVerificationRepository.invalidateAllByEmail(email, LocalDateTime.now());
    }

    private String gerarCodigo() {
        // Gerar código de 6 dígitos
        int codigo = secureRandom.nextInt(900000) + 100000;
        return String.valueOf(codigo);
    }

    private void enviarEmailComCodigo(String email, String codigo, String tipoUsuario) {
        try {
            String assunto = "Código de Verificação - Sistema ACLP TJBA";
            String conteudo = String.format("""
                Olá,
                
                Seu código de verificação para o Sistema ACLP é: %s
                
                Este código é válido por %d minutos.
                Tipo de usuário: %s
                
                Se você não solicitou este código, ignore este email.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, codigo, VALIDADE_MINUTOS, tipoUsuario.equals("ADMIN") ? "Administrador" : "Usuário");

            emailService.enviarEmail(email, assunto, conteudo);

        } catch (Exception e) {
            log.error("Erro ao enviar email para: " + email, e);
            throw new RuntimeException("Erro ao enviar código por email. Tente novamente.");
        }
    }

    private String mascarEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];

        if (local.length() <= 3) {
            return local.charAt(0) + "***@" + domain;
        }

        return local.substring(0, 2) + "***" + local.substring(local.length() - 1) + "@" + domain;
    }

    private String gerarHashPublico(Long id) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = id + "_" + System.currentTimeMillis();
            byte[] hash = md.digest(input.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(id.hashCode());
        }
    }

    private String gerarTokenVerificacao(EmailVerification verification) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = verification.getId() + "_" + verification.getEmail() + "_" +
                    verification.getVerificadoEm() + "_" + "ACLP_SECRET";
            byte[] hash = md.digest(input.getBytes());
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar token de verificação");
        }
    }

    /**
     * Limpeza automática de códigos expirados (executar periodicamente)
     */
    @Transactional
    public void limparCodigosExpirados() {
        log.debug("Executando limpeza de códigos expirados");

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limiteLimpeza = agora.minusDays(7); // Remove verificados há mais de 7 dias

        int expirados = emailVerificationRepository.deleteExpiredCodes(agora);
        int verificadosAntigos = emailVerificationRepository.deleteOldVerifiedCodes(limiteLimpeza);

        if (expirados > 0 || verificadosAntigos > 0) {
            log.info("Limpeza concluída - Removidos: {} expirados, {} verificados antigos",
                    expirados, verificadosAntigos);
        }
    }
}