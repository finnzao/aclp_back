package br.jus.tjba.aclp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import br.jus.tjba.aclp.model.Usuario;

/**
 * Serviço de auditoria para rastrear ações importantes no sistema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    public void logLogin(Usuario usuario, String ipAddress, String status) {
        log.info("AUDIT - Login: user={}, ip={}, status={}", usuario.getEmail(), ipAddress, status);
        // Implementar persistência em tabela de auditoria
    }

    public void logLogin(String email, String ipAddress, String status) {
        log.info("AUDIT - Login Attempt: email={}, ip={}, status={}", email, ipAddress, status);
    }

    public void logLogout(String email, String ipAddress, String status) {
        log.info("AUDIT - Logout: email={}, ip={}, status={}", email, ipAddress, status);
    }

    public void logPasswordResetRequest(String email, String status) {
        log.info("AUDIT - Password Reset Request: email={}, status={}", email, status);
    }

    public void logPasswordReset(String email, String status) {
        log.info("AUDIT - Password Reset: email={}, status={}", email, status);
    }

    public void logPasswordChange(String email, String status) {
        log.info("AUDIT - Password Change: email={}, status={}", email, status);
    }

    public void logMfaSetup(String email, String status) {
        log.info("AUDIT - MFA Setup: email={}, status={}", email, status);
    }

    public void logSessionInvalidation(String email, String sessionId, String reason) {
        log.info("AUDIT - Session Invalidated: email={}, session={}, reason={}", email, sessionId, reason);
    }

    public void logSuspiciousActivity(String email, String ipAddress, String activity) {
        log.warn("AUDIT - SUSPICIOUS: email={}, ip={}, activity={}", email, ipAddress, activity);
    }
}