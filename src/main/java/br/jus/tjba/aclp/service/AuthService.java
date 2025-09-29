package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.AuthDTO.*;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.model.RefreshToken;
import br.jus.tjba.aclp.model.LoginAttempt;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import br.jus.tjba.aclp.repository.RefreshTokenRepository;
import br.jus.tjba.aclp.repository.LoginAttemptRepository;
import br.jus.tjba.aclp.security.JwtTokenProvider;
import br.jus.tjba.aclp.exception.AuthenticationException;
import br.jus.tjba.aclp.exception.AccountLockedException;
import br.jus.tjba.aclp.exception.InvalidTokenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;  // ✅ CORRIGIDO: jakarta em vez de javax
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço principal de autenticação e autorização
 * Gerencia login, logout, tokens JWT, refresh tokens e controle de sessão
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final AuditService auditService;

    // Cache de sessões ativas (para controle em memória)
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    // Configurações
    @Value("${aclp.auth.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${aclp.auth.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${aclp.auth.session-timeout-minutes:60}")
    private int sessionTimeoutMinutes;

    @Value("${aclp.auth.max-concurrent-sessions:3}")
    private int maxConcurrentSessions;

    @Value("${aclp.auth.refresh-token-validity-days:7}")
    private int refreshTokenValidityDays;

    @Value("${aclp.auth.password-reset-token-hours:2}")
    private int passwordResetTokenHours;

    @Value("${aclp.auth.mfa-enabled:false}")
    private boolean mfaEnabled;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Realiza o login do usuário
     */
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request, HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("Tentativa de login - Email: {}, IP: {}", request.getEmail(), ipAddress);

        try {
            // 1. Verificar rate limiting por IP
            checkRateLimiting(ipAddress);

            // 2. Buscar usuário
            Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

            // 3. Verificar se conta está bloqueada
            checkAccountLocked(usuario);

            // 4. Verificar se usuário está ativo
            if (!usuario.getAtivo()) {
                throw new DisabledException("Conta desativada");
            }

            // 5. Autenticar credenciais
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
            );

            // 6. Verificar 2FA se habilitado
            if (usuario.getMfaEnabled() && mfaEnabled) {
                return handleMfaAuthentication(usuario, request, ipAddress);
            }

            // 7. Verificar sessões concorrentes
            handleConcurrentSessions(usuario, request.isForceLogin());

            // 8. Gerar tokens
            String accessToken = jwtTokenProvider.generateToken(usuario);
            String refreshToken = generateRefreshToken(usuario, ipAddress, userAgent);

            // 9. Criar sessão
            SessionInfo session = createSession(usuario, accessToken, ipAddress, userAgent);

            // 10. Atualizar usuário
            usuario.resetarTentativas();
            usuario.registrarLogin();
            usuarioRepository.save(usuario);

            // 11. Registrar login bem-sucedido
            registerSuccessfulLogin(usuario, ipAddress, userAgent);

            // 12. Auditar
            auditService.logLogin(usuario, ipAddress, "SUCCESS");

            log.info("Login bem-sucedido - Usuário: {}, Session: {}", usuario.getEmail(), session.getSessionId());

            return LoginResponseDTO.builder()
                    .success(true)
                    .message("Login realizado com sucesso")
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getTokenValidity())
                    .sessionId(session.getSessionId())
                    .usuario(toUsuarioDTO(usuario))
                    .build();

        } catch (BadCredentialsException e) {
            handleFailedLogin(request.getEmail(), ipAddress);
            throw new AuthenticationException("Email ou senha incorretos");

        } catch (DisabledException e) {
            auditService.logLogin(request.getEmail(), ipAddress, "DISABLED");
            throw new AuthenticationException("Conta desativada. Entre em contato com o administrador.");

        } catch (AccountLockedException e) {
            throw e; // Re-throw as is

        } catch (Exception e) {
            log.error("Erro no login - Email: {}, IP: {}", request.getEmail(), ipAddress, e);
            throw new AuthenticationException("Erro ao realizar login. Tente novamente.");
        }
    }

    /**
     * Realiza logout do usuário
     */
    @Transactional
    public void logout(String token, HttpServletRequest request) {
        try {
            String email = jwtTokenProvider.getEmailFromToken(token);
            String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
            String ipAddress = extractIpAddress(request);

            log.info("Logout solicitado - Email: {}, Session: {}", email, sessionId);

            // 1. Invalidar token JWT (adicionar à blacklist)
            jwtTokenProvider.invalidateToken(token);

            // 2. Remover refresh tokens
            refreshTokenRepository.deleteByUsuarioEmail(email);

            // 3. Remover sessão ativa
            activeSessions.remove(sessionId);

            // 4. Auditar
            auditService.logLogout(email, ipAddress, "SUCCESS");

            log.info("Logout realizado - Email: {}", email);

        } catch (Exception e) {
            log.error("Erro no logout", e);
            // Logout sempre deve ter sucesso para o cliente
        }
    }

    /**
     * Renova o token de acesso usando refresh token
     */
    @Transactional
    public RefreshTokenResponseDTO refreshToken(RefreshTokenRequestDTO request, HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);

        log.debug("Renovação de token solicitada - IP: {}", ipAddress);

        // 1. Validar refresh token
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token inválido"));

        // 2. Verificar expiração
        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token expirado");
        }

        // 3. Verificar se foi revogado
        if (refreshToken.isRevoked()) {
            throw new InvalidTokenException("Refresh token revogado");
        }

        // 4. Verificar usuário
        Usuario usuario = refreshToken.getUsuario();
        if (!usuario.getAtivo() || !usuario.podeLogar()) {
            throw new AuthenticationException("Usuário não autorizado");
        }

        // 5. Gerar novo access token
        String newAccessToken = jwtTokenProvider.generateToken(usuario);

        // 6. Opcionalmente, rotacionar refresh token
        String newRefreshToken = null;
        if (shouldRotateRefreshToken(refreshToken)) {
            refreshToken.revogar();
            refreshTokenRepository.save(refreshToken);

            newRefreshToken = generateRefreshToken(usuario, ipAddress, httpRequest.getHeader("User-Agent"));
        }

        // 7. Atualizar sessão
        updateSession(usuario.getEmail(), newAccessToken);

        log.info("Token renovado - Usuário: {}", usuario.getEmail());

        return RefreshTokenResponseDTO.builder()
                .success(true)
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken != null ? newRefreshToken : request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getTokenValidity())
                .build();
    }

    /**
     * Valida token JWT
     */
    public TokenValidationResponseDTO validateToken(String token) {
        try {
            boolean isValid = jwtTokenProvider.validateToken(token);

            if (!isValid) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Token inválido")
                        .build();
            }

            String email = jwtTokenProvider.getEmailFromToken(token);
            Date expiration = jwtTokenProvider.getExpirationDateFromToken(token);

            // Verificar se token está na blacklist
            if (jwtTokenProvider.isTokenBlacklisted(token)) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Token revogado")
                        .build();
            }

            // Verificar se usuário ainda existe e está ativo
            Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
            if (usuario.isEmpty() || !usuario.get().getAtivo()) {
                return TokenValidationResponseDTO.builder()
                        .valid(false)
                        .message("Usuário inválido")
                        .build();
            }

            return TokenValidationResponseDTO.builder()
                    .valid(true)
                    .email(email)
                    .expiration(expiration)
                    .authorities(jwtTokenProvider.getAuthoritiesFromToken(token))
                    .message("Token válido")
                    .build();

        } catch (Exception e) {
            log.error("Erro ao validar token", e);
            return TokenValidationResponseDTO.builder()
                    .valid(false)
                    .message("Erro ao validar token")
                    .build();
        }
    }

    /**
     * Inicia processo de recuperação de senha
     */
    @Transactional
    public void requestPasswordReset(PasswordResetRequestDTO request) {
        String email = request.getEmail().trim().toLowerCase();

        log.info("Recuperação de senha solicitada - Email: {}", email);

        // Sempre retornar sucesso para não revelar se email existe
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario != null && usuario.getAtivo()) {
            String resetToken = generatePasswordResetToken();
            LocalDateTime expiry = LocalDateTime.now().plusHours(passwordResetTokenHours);

            // Salvar token de reset
            usuario.setPasswordResetToken(resetToken);
            usuario.setPasswordResetExpiry(expiry);
            usuarioRepository.save(usuario);

            // Enviar email
            sendPasswordResetEmail(usuario, resetToken);

            auditService.logPasswordResetRequest(email, "SUCCESS");
        }

        // Sempre retornar sucesso
        log.info("Email de recuperação processado - Email: {}", email);
    }

    /**
     * Reseta a senha usando token
     */
    @Transactional
    public void resetPassword(PasswordResetConfirmDTO request) {
        log.info("Reset de senha solicitado");

        Usuario usuario = usuarioRepository.findByPasswordResetToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("Token inválido"));

        // Verificar expiração
        if (usuario.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token expirado");
        }

        // Validar nova senha
        validatePasswordStrength(request.getNovaSenha());

        // Atualizar senha
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setPasswordResetToken(null);
        usuario.setPasswordResetExpiry(null);
        usuario.setDeveTrocarSenha(false);
        usuario.setUltimoResetSenha(LocalDateTime.now());

        // Invalidar todas as sessões e tokens
        invalidateAllUserSessions(usuario.getEmail());

        usuarioRepository.save(usuario);

        // Notificar usuário
        sendPasswordChangedEmail(usuario);

        auditService.logPasswordReset(usuario.getEmail(), "SUCCESS");

        log.info("Senha resetada com sucesso - Email: {}", usuario.getEmail());
    }

    /**
     * Altera senha do usuário autenticado
     */
    @Transactional
    public void changePassword(ChangePasswordDTO request, String userEmail) {
        log.info("Alteração de senha solicitada - Email: {}", userEmail);

        Usuario usuario = usuarioRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AuthenticationException("Usuário não encontrado"));

        // Verificar senha atual
        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            throw new AuthenticationException("Senha atual incorreta");
        }

        // Validar nova senha
        validatePasswordStrength(request.getNovaSenha());

        // Verificar se não é igual à anterior
        if (passwordEncoder.matches(request.getNovaSenha(), usuario.getSenha())) {
            throw new IllegalArgumentException("Nova senha não pode ser igual à anterior");
        }

        // Atualizar senha
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.setDeveTrocarSenha(false);
        usuario.setUltimoResetSenha(LocalDateTime.now());
        usuario.setSenhaExpiraEm(LocalDateTime.now().plusDays(90)); // Política de 90 dias

        usuarioRepository.save(usuario);

        // Notificar
        sendPasswordChangedEmail(usuario);

        auditService.logPasswordChange(userEmail, "SUCCESS");

        log.info("Senha alterada com sucesso - Email: {}", userEmail);
    }

    /**
     * Retorna o usuário autenticado atual
     */
    public Usuario getUsuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String email = authentication.getName();
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    /**
     * Retorna usuário mock para desenvolvimento (substituir em produção)
     */
    public Usuario getMockAdminUser() {
        return usuarioRepository.findByEmail("admin@tjba.jus.br")
                .orElseGet(() -> {
                    log.warn("Admin mock não encontrado, retornando primeiro admin do sistema");
                    return usuarioRepository.findByTipo(br.jus.tjba.aclp.model.enums.TipoUsuario.ADMIN)
                            .stream()
                            .findFirst()
                            .orElse(null);
                });
    }

    /**
     * Retorna informações da sessão atual
     */
    public SessionInfoDTO getCurrentSessionInfo(String token) {
        try {
            String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
            SessionInfo session = activeSessions.get(sessionId);

            if (session == null) {
                return null;
            }

            return SessionInfoDTO.builder()
                    .sessionId(session.getSessionId())
                    .userEmail(session.getUserEmail())
                    .ipAddress(session.getIpAddress())
                    .userAgent(session.getUserAgent())
                    .loginTime(session.getLoginTime())
                    .lastActivity(session.getLastActivity())
                    .expiresAt(session.getExpiresAt())
                    .build();

        } catch (Exception e) {
            log.error("Erro ao obter info da sessão", e);
            return null;
        }
    }

    /**
     * Lista todas as sessões ativas de um usuário
     */
    public List<SessionInfoDTO> getUserSessions(String userEmail) {
        return activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(userEmail))
                .map(s -> SessionInfoDTO.builder()
                        .sessionId(s.getSessionId())
                        .userEmail(s.getUserEmail())
                        .ipAddress(s.getIpAddress())
                        .userAgent(s.getUserAgent())
                        .loginTime(s.getLoginTime())
                        .lastActivity(s.getLastActivity())
                        .expiresAt(s.getExpiresAt())
                        .build())
                .toList();
    }

    /**
     * Invalida uma sessão específica
     */
    @Transactional
    public void invalidateSession(String sessionId, String requestingUser) {
        SessionInfo session = activeSessions.get(sessionId);

        if (session != null) {
            // Verificar permissão (própria sessão ou admin)
            Usuario usuario = getUsuarioAtual();
            if (!session.getUserEmail().equals(requestingUser) && !usuario.isAdmin()) {
                throw new AuthenticationException("Sem permissão para invalidar esta sessão");
            }

            activeSessions.remove(sessionId);

            // Invalidar refresh tokens associados
            refreshTokenRepository.deleteByUsuarioEmail(session.getUserEmail());

            log.info("Sessão invalidada - ID: {}, Usuário: {}", sessionId, session.getUserEmail());
        }
    }

    /**
     * Invalida todas as sessões de um usuário
     */
    @Transactional
    public void invalidateAllUserSessions(String userEmail) {
        // Remover sessões em memória
        activeSessions.entrySet().removeIf(entry ->
                entry.getValue().getUserEmail().equals(userEmail));

        // Remover refresh tokens
        refreshTokenRepository.deleteByUsuarioEmail(userEmail);

        // Adicionar tokens à blacklist
        jwtTokenProvider.blacklistUserTokens(userEmail);

        log.info("Todas as sessões invalidadas - Usuário: {}", userEmail);
    }

    // ========== MÉTODOS PRIVADOS ==========

    private void checkRateLimiting(String ipAddress) {
        long recentAttempts = loginAttemptRepository.countRecentAttemptsByIp(
                ipAddress, LocalDateTime.now().minusMinutes(1));

        if (recentAttempts > 10) {
            throw new AuthenticationException("Muitas tentativas. Aguarde antes de tentar novamente.");
        }
    }

    private void checkAccountLocked(Usuario usuario) {
        if (usuario.getBloqueadoAte() != null &&
                usuario.getBloqueadoAte().isAfter(LocalDateTime.now())) {
            long minutosRestantes = java.time.Duration.between(
                    LocalDateTime.now(), usuario.getBloqueadoAte()).toMinutes();
            throw new AccountLockedException(
                    String.format("Conta bloqueada. Tente novamente em %d minutos.", minutosRestantes));
        }
    }

    private void handleFailedLogin(String email, String ipAddress) {
        // Registrar tentativa falhada
        LoginAttempt attempt = LoginAttempt.builder()
                .email(email)
                .ipAddress(ipAddress)
                .success(false)
                .attemptTime(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);

        // Incrementar contador no usuário
        usuarioRepository.findByEmail(email).ifPresent(usuario -> {
            usuario.incrementarTentativasFalhadas();
            usuarioRepository.save(usuario);

            // Notificar se conta foi bloqueada
            if (usuario.getBloqueadoAte() != null) {
                sendAccountLockedEmail(usuario);
            }
        });
    }

    private void registerSuccessfulLogin(Usuario usuario, String ipAddress, String userAgent) {
        LoginAttempt attempt = LoginAttempt.builder()
                .email(usuario.getEmail())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .success(true)
                .attemptTime(LocalDateTime.now())
                .build();
        loginAttemptRepository.save(attempt);
    }

    private String generateRefreshToken(Usuario usuario, String ipAddress, String userAgent) {
        // Gerar token único
        String tokenValue = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .usuario(usuario)
                .expiryDate(LocalDateTime.now().plusDays(refreshTokenValidityDays))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        refreshTokenRepository.save(refreshToken);

        return tokenValue;
    }

    private SessionInfo createSession(Usuario usuario, String token, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();

        SessionInfo session = SessionInfo.builder()
                .sessionId(sessionId)
                .userEmail(usuario.getEmail())
                .userId(usuario.getId())
                .token(token)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .loginTime(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
                .build();

        activeSessions.put(sessionId, session);

        return session;
    }

    private void updateSession(String userEmail, String newToken) {
        activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(userEmail))
                .forEach(s -> {
                    s.setToken(newToken);
                    s.setLastActivity(LocalDateTime.now());
                });
    }

    private void handleConcurrentSessions(Usuario usuario, boolean forceLogin) {
        List<SessionInfo> userSessions = activeSessions.values().stream()
                .filter(s -> s.getUserEmail().equals(usuario.getEmail()))
                .toList();

        if (userSessions.size() >= maxConcurrentSessions) {
            if (forceLogin) {
                // Remover sessão mais antiga
                userSessions.stream()
                        .min(Comparator.comparing(SessionInfo::getLoginTime))
                        .ifPresent(s -> activeSessions.remove(s.getSessionId()));
            } else {
                throw new AuthenticationException(
                        String.format("Limite de %d sessões simultâneas atingido", maxConcurrentSessions));
            }
        }
    }

    private boolean shouldRotateRefreshToken(RefreshToken token) {
        // Rotacionar se token tem mais de 1 dia
        return token.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1));
    }

    private LoginResponseDTO handleMfaAuthentication(Usuario usuario, LoginRequestDTO request, String ipAddress) {
        // Se código MFA não foi fornecido, solicitar
        if (request.getMfaCode() == null || request.getMfaCode().isEmpty()) {
            return LoginResponseDTO.builder()
                    .success(false)
                    .requiresMfa(true)
                    .message("Código de autenticação necessário")
                    .build();
        }

        // Validar código MFA
        if (!validateMfaCode(usuario, request.getMfaCode())) {
            throw new AuthenticationException("Código de autenticação inválido");
        }

        // Continuar com login normal
        return null; // Continuar fluxo normal
    }

    private boolean validateMfaCode(Usuario usuario, String code) {
        // Implementar validação TOTP
        // Por enquanto, retorna true (implementar com biblioteca como GoogleAuth)
        return true;
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Senha deve ter pelo menos 8 caracteres");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra maiúscula");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra minúscula");
        }

        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos um número");
        }

        if (!password.matches(".*[@$!%*?&#].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos um caractere especial");
        }
    }

    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private UsuarioDTO toUsuarioDTO(Usuario usuario) {
        return UsuarioDTO.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .tipo(usuario.getTipo())
                .departamento(usuario.getDepartamento())
                .telefone(usuario.getTelefone())
                .ultimoLogin(usuario.getUltimoLogin())
                .mfaEnabled(usuario.getMfaEnabled())
                .build();
    }

    private void sendPasswordResetEmail(Usuario usuario, String token) {
        String resetLink = String.format("%s/reset-password?token=%s",
                "http://localhost:3000", token); // Configurar URL base

        String content = String.format("""
                Olá %s,
                
                Você solicitou a recuperação de senha para o Sistema ACLP.
                
                Clique no link abaixo para criar uma nova senha:
                %s
                
                Este link é válido por %d horas.
                
                Se você não solicitou esta recuperação, ignore este email.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), resetLink, passwordResetTokenHours);

        emailService.enviarEmail(usuario.getEmail(), "Recuperação de Senha - ACLP", content);
    }

    private void sendPasswordChangedEmail(Usuario usuario) {
        String content = String.format("""
                Olá %s,
                
                Sua senha do Sistema ACLP foi alterada com sucesso.
                
                Se você não realizou esta alteração, entre em contato imediatamente com o suporte.
                
                Data/Hora: %s
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), LocalDateTime.now());

        emailService.enviarEmail(usuario.getEmail(), "Senha Alterada - ACLP", content);
    }

    private void sendAccountLockedEmail(Usuario usuario) {
        String content = String.format("""
                Olá %s,
                
                Sua conta foi temporariamente bloqueada devido a múltiplas tentativas de login falhadas.
                
                A conta será desbloqueada automaticamente em %d minutos.
                
                Se não foi você, entre em contato com o suporte.
                
                Atenciosamente,
                Sistema ACLP - TJBA
                """, usuario.getNome(), lockoutDurationMinutes);

        emailService.enviarEmail(usuario.getEmail(), "Conta Bloqueada - ACLP", content);
    }

    // ========== CLASSES INTERNAS ==========

    @lombok.Data
    @lombok.Builder
    private static class SessionInfo {
        private String sessionId;
        private Long userId;
        private String userEmail;
        private String token;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime loginTime;
        private LocalDateTime lastActivity;
        private LocalDateTime expiresAt;
    }
}