package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.ConviteDTO.*;
import br.jus.tjba.aclp.model.Convite;
import br.jus.tjba.aclp.model.PreCadastro;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.enums.StatusConvite;
import br.jus.tjba.aclp.model.enums.StatusUsuario;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.repository.ConviteRepository;
import br.jus.tjba.aclp.repository.PreCadastroRepository;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço para gerenciar verificação de email em duas etapas
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final PreCadastroRepository preCadastroRepository;
    private final ConviteRepository conviteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    // ========== DTOs INTERNOS PARA COMPATIBILIDADE ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolicitarCodigoDTO {
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;
        private String nome;
        private TipoUsuario tipoUsuario;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolicitarCodigoResponseDTO {
        private Boolean success;
        private String message;
        private String email;
        private LocalDateTime expiraEm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificarCodigoDTO {
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;
        @NotBlank(message = "Código é obrigatório")
        private String codigo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificarCodigoResponseDTO {
        private Boolean success;
        private Boolean emailVerificado;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusVerificacaoDTO {
        private String email;
        private Boolean emailVerificado;
        private Boolean codigoEnviado;
        private LocalDateTime expiraEm;
        private Integer tentativasRestantes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReenviarCodigoDTO {
        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenValidoDTO {
        private Boolean valido;
        private String email;
        private String mensagem;
    }

    // ========== MÉTODOS PRINCIPAIS DO FLUXO DE CONVITES ==========

    /**
     * ETAPA 1: Cria pré-cadastro e envia email de verificação
     * Usado no fluxo de convites
     */
    @Transactional
    public PreCadastroResponse criarPreCadastro(PreCadastroRequest request, HttpServletRequest httpRequest) {
        log.info("Iniciando pré-cadastro - Token: {}", request.getToken());

        // Validar convite
        Convite convite = conviteRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Convite não encontrado"));

        if (!convite.isValido()) {
            throw new IllegalArgumentException("Convite inválido ou expirado");
        }

        // Validar email
        String email = request.getEmail().toLowerCase().trim();

        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Este email já está cadastrado");
        }

        if (preCadastroRepository.existsByEmailAndVerificadoFalse(email)) {
            throw new IllegalArgumentException("Já existe um cadastro pendente para este email. Verifique sua caixa de entrada.");
        }

        // Validar senhas
        if (!request.senhasCoincidentes()) {
            throw new IllegalArgumentException("As senhas não coincidem");
        }

        // Criar pré-cadastro
        PreCadastro preCadastro = PreCadastro.builder()
                .tokenConvite(convite.getToken())
                .email(email)
                .nome(request.getNome())
                .senha(passwordEncoder.encode(request.getSenha()))
                .tipoUsuario(convite.getTipoUsuario())
                .comarca(convite.getComarca())
                .departamento(convite.getDepartamento())
                .cargo(request.getCargo())
                .ipCadastro(extractIpAddress(httpRequest))
                .build();

        preCadastro = preCadastroRepository.save(preCadastro);

        // Enviar email de verificação
        try {
            enviarEmailVerificacao(preCadastro);
            log.info("Email de verificação enviado para: {}", email);
        } catch (Exception e) {
            log.error("Erro ao enviar email de verificação: {}", e.getMessage());
            // Não falhar o processo se o email não for enviado
        }

        return PreCadastroResponse.builder()
                .success(true)
                .message("Cadastro iniciado! Verifique seu email para confirmar a conta.")
                .email(email)
                .expiracaoVerificacao(preCadastro.getExpiraEm())
                .build();
    }

    /**
     * ETAPA 2: Verifica email e cria usuário definitivo
     */
    @Transactional
    public VerificarEmailResponse verificarEmail(String token, HttpServletRequest httpRequest) {
        log.info("Verificando email - Token: {}", token);

        // Buscar pré-cadastro
        PreCadastro preCadastro = preCadastroRepository.findByTokenVerificacao(token)
                .orElseThrow(() -> new IllegalArgumentException("Token de verificação inválido"));

        // Validar status
        if (preCadastro.isVerificado()) {
            throw new IllegalArgumentException("Este email já foi verificado");
        }

        if (preCadastro.isExpirado()) {
            throw new IllegalArgumentException("Token de verificação expirado. Faça um novo cadastro.");
        }

        if (preCadastro.excedeuTentativas()) {
            throw new IllegalArgumentException("Muitas tentativas de verificação. Token bloqueado.");
        }

        // Incrementar tentativas
        preCadastro.incrementarTentativas();

        // Verificar novamente se email já existe (dupla verificação)
        if (usuarioRepository.existsByEmail(preCadastro.getEmail())) {
            throw new IllegalArgumentException("Este email já está cadastrado");
        }

        // Criar usuário definitivo
        Usuario usuario = Usuario.builder()
                .nome(preCadastro.getNome())
                .email(preCadastro.getEmail())
                .senha(preCadastro.getSenha()) // Já está criptografada
                .tipo(preCadastro.getTipoUsuario())
                .comarca(preCadastro.getComarca())
                .departamento(preCadastro.getDepartamento())
                .cargo(preCadastro.getCargo())
                .ativo(true)
                .statusUsuario(StatusUsuario.ACTIVE)
                .emailVerificado(true)
                .dataVerificacaoEmail(LocalDateTime.now())
                .deveTrocarSenha(false)
                .build();

        usuario = usuarioRepository.save(usuario);

        // Marcar pré-cadastro como verificado
        preCadastro.marcarVerificado(extractIpAddress(httpRequest), usuario.getId());
        preCadastroRepository.save(preCadastro);

        // Atualizar convite original - CORRIGIDO
        Convite convite = conviteRepository.findByToken(preCadastro.getTokenConvite()).orElse(null);
        if (convite != null && convite.getStatus() == StatusConvite.PENDENTE) {
            // Usar o método ativar() ao invés de registrarUso()
            convite.ativar(usuario, extractIpAddress(httpRequest));
            conviteRepository.save(convite);
        }

        // Enviar email de boas-vindas
        try {
            enviarEmailBoasVindas(usuario);
        } catch (Exception e) {
            log.error("Erro ao enviar email de boas-vindas: {}", e.getMessage());
        }

        log.info("Usuário criado com sucesso - Email: {}, ID: {}", usuario.getEmail(), usuario.getId());

        return VerificarEmailResponse.builder()
                .success(true)
                .message("Email verificado com sucesso! Sua conta foi ativada.")
                .usuario(UsuarioInfoDTO.builder()
                        .id(usuario.getId())
                        .nome(usuario.getNome())
                        .email(usuario.getEmail())
                        .tipo(usuario.getTipo())
                        .comarca(usuario.getComarca())
                        .departamento(usuario.getDepartamento())
                        .cargo(usuario.getCargo())
                        .build())
                .loginUrl("/login")
                .build();
    }

    /**
     * Reenvia email de verificação
     */
    @Transactional
    public void reenviarEmailVerificacao(String email) {
        log.info("Reenviando email de verificação para: {}", email);

        PreCadastro preCadastro = preCadastroRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Pré-cadastro não encontrado"));

        if (preCadastro.isVerificado()) {
            throw new IllegalArgumentException("Este email já foi verificado");
        }

        if (preCadastro.isExpirado()) {
            throw new IllegalArgumentException("Pré-cadastro expirado. Faça um novo cadastro.");
        }

        // Enviar email
        enviarEmailVerificacao(preCadastro);
        log.info("Email de verificação reenviado");
    }

    // ========== MÉTODOS ADICIONAIS PARA COMPATIBILIDADE ==========

    /**
     * Solicita código de verificação (método alternativo)
     * Compatibilidade com EmailVerificationController existente
     */
    @Transactional
    public SolicitarCodigoResponseDTO solicitarCodigo(SolicitarCodigoDTO request, String ipAddress) {
        log.info("Solicitando código de verificação - Email: {}", request.getEmail());

        String email = request.getEmail().toLowerCase().trim();

        // Verificar se email já existe
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Este email já está cadastrado");
        }

        // Criar ou atualizar pré-cadastro simplificado
        PreCadastro preCadastro = preCadastroRepository.findByEmail(email)
                .orElse(PreCadastro.builder()
                        .email(email)
                        .nome(request.getNome() != null ? request.getNome() : "Usuário")
                        .tokenConvite("temp-" + UUID.randomUUID())
                        .tokenVerificacao("ver-" + UUID.randomUUID())
                        .senha(passwordEncoder.encode(UUID.randomUUID().toString())) // Senha temporária
                        .tipoUsuario(request.getTipoUsuario() != null ? request.getTipoUsuario() : TipoUsuario.USUARIO)
                        .ipCadastro(ipAddress)
                        .build());

        // Gerar novo token se expirou
        if (preCadastro.isExpirado() || preCadastro.isVerificado()) {
            preCadastro.setTokenVerificacao("ver-" + UUID.randomUUID());
            preCadastro.setExpiraEm(LocalDateTime.now().plusHours(24));
            preCadastro.setVerificado(false);
            preCadastro.setTentativasVerificacao(0);
        }

        preCadastroRepository.save(preCadastro);

        // Enviar email
        enviarEmailVerificacao(preCadastro);

        return SolicitarCodigoResponseDTO.builder()
                .success(true)
                .message("Código de verificação enviado para " + email)
                .email(email)
                .expiraEm(preCadastro.getExpiraEm())
                .build();
    }

    /**
     * Verifica código (compatibilidade)
     */
    @Transactional
    public VerificarCodigoResponseDTO verificarCodigo(VerificarCodigoDTO request, String ipAddress) {
        log.info("Verificando código - Email: {}", request.getEmail());

        PreCadastro preCadastro = preCadastroRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Código inválido"));

        // Aqui você poderia implementar verificação de código numérico
        // Por enquanto, vamos usar o token
        if (!preCadastro.getTokenVerificacao().endsWith(request.getCodigo())) {
            throw new IllegalArgumentException("Código incorreto");
        }

        preCadastro.setVerificado(true);
        preCadastro.setVerificadoEm(LocalDateTime.now());
        preCadastroRepository.save(preCadastro);

        return VerificarCodigoResponseDTO.builder()
                .success(true)
                .emailVerificado(true)
                .message("Email verificado com sucesso")
                .build();
    }

    /**
     * Consulta status de verificação
     */
    @Transactional(readOnly = true)
    public StatusVerificacaoDTO consultarStatus(String email) {
        Optional<PreCadastro> preCadastroOpt = preCadastroRepository.findByEmail(email.toLowerCase());

        if (preCadastroOpt.isEmpty()) {
            return StatusVerificacaoDTO.builder()
                    .email(email)
                    .emailVerificado(false)
                    .codigoEnviado(false)
                    .build();
        }

        PreCadastro preCadastro = preCadastroOpt.get();

        return StatusVerificacaoDTO.builder()
                .email(email)
                .emailVerificado(preCadastro.isVerificado())
                .codigoEnviado(!preCadastro.isExpirado())
                .expiraEm(preCadastro.getExpiraEm())
                .tentativasRestantes(5 - preCadastro.getTentativasVerificacao())
                .build();
    }

    /**
     * Reenvia código (compatibilidade)
     */
    @Transactional
    public void reenviarCodigo(ReenviarCodigoDTO request, String ipAddress) {
        reenviarEmailVerificacao(request.getEmail());
    }

    /**
     * Valida token de verificação (compatibilidade)
     */
    @Transactional
    public TokenValidoDTO validarTokenVerificacao(String token, String ipAddress) {
        Optional<PreCadastro> preCadastroOpt = preCadastroRepository.findByTokenVerificacao(token);

        if (preCadastroOpt.isEmpty()) {
            return TokenValidoDTO.builder()
                    .valido(false)
                    .mensagem("Token inválido")
                    .build();
        }

        PreCadastro preCadastro = preCadastroOpt.get();

        if (preCadastro.isExpirado()) {
            return TokenValidoDTO.builder()
                    .valido(false)
                    .mensagem("Token expirado")
                    .build();
        }

        if (preCadastro.isVerificado()) {
            return TokenValidoDTO.builder()
                    .valido(false)
                    .mensagem("Token já utilizado")
                    .build();
        }

        return TokenValidoDTO.builder()
                .valido(true)
                .email(preCadastro.getEmail())
                .mensagem("Token válido")
                .build();
    }

    /**
     * Job para limpar pré-cadastros expirados
     * Executa diariamente às 3h da manhã
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void limparPreCadastrosExpirados() {
        log.info("Iniciando limpeza de pré-cadastros expirados");

        LocalDateTime dataLimite = LocalDateTime.now().minusDays(7); // Remove após 7 dias
        preCadastroRepository.deleteExpirados(dataLimite);

        log.info("Limpeza de pré-cadastros concluída");
    }

    // ========== MÉTODOS AUXILIARES ==========

    private void enviarEmailVerificacao(PreCadastro preCadastro) {
        String linkVerificacao = String.format("%s/verificar-email/%s",
                frontendUrl, preCadastro.getTokenVerificacao());

        String assunto = "Confirme seu Email - Sistema ACLP";

        String conteudo = String.format("""
                Olá %s,
                
                Você está quase lá! Clique no botão abaixo para confirmar seu email e ativar sua conta no Sistema ACLP:
                
                %s
                
                Ou copie e cole este link no seu navegador:
                %s
                
                Seus dados de cadastro:
                ━━━━━━━━━━━━━━━━━━━━━
                Email: %s
                Tipo de acesso: %s
                Comarca: %s
                Departamento: %s
                Cargo: %s
                ━━━━━━━━━━━━━━━━━━━━━
                
                ⚠️ Este link expira em 24 horas (%s)
                
                Após confirmar, você poderá fazer login com seu email e senha cadastrados.
                
                Se você não solicitou este cadastro, ignore este email.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """,
                preCadastro.getNome(),
                linkVerificacao,
                linkVerificacao,
                preCadastro.getEmail(),
                preCadastro.getTipoUsuario() != null ? preCadastro.getTipoUsuario().getLabel() : "Usuário",
                preCadastro.getComarca() != null ? preCadastro.getComarca() : "Não definida",
                preCadastro.getDepartamento() != null ? preCadastro.getDepartamento() : "Não definido",
                preCadastro.getCargo() != null ? preCadastro.getCargo() : "Não definido",
                preCadastro.getExpiraEm()
        );

        emailService.enviarEmail(preCadastro.getEmail(), assunto, conteudo);
    }

    private void enviarEmailBoasVindas(Usuario usuario) {
        String assunto = "Bem-vindo ao Sistema ACLP - TJBA";

        String conteudo = String.format("""
                Olá %s,
                
                Sua conta no Sistema ACLP foi ativada com sucesso!
                
                Seus dados de acesso:
                ━━━━━━━━━━━━━━━━━━━━━
                Email (login): %s
                Tipo de acesso: %s
                Comarca: %s
                Departamento: %s
                ━━━━━━━━━━━━━━━━━━━━━
                
                Você já pode acessar o sistema em:
                %s
                
                Use seu email e a senha que você cadastrou para fazer login.
                
                Em caso de dúvidas, entre em contato com o suporte.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """,
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTipo().getLabel(),
                usuario.getComarca() != null ? usuario.getComarca() : "Não definida",
                usuario.getDepartamento() != null ? usuario.getDepartamento() : "Não definido",
                frontendUrl
        );

        emailService.enviarEmail(usuario.getEmail(), assunto, conteudo);
    }

    private String extractIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}