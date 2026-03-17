package br.jus.tjba.aclp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementação in-memory da blacklist de tokens JWT.
 * 
 * Ativada quando aclp.redis.enabled=false (ex: deploy no Render sem Redis).
 * Substitui o RedisTokenBlacklistService automaticamente via @Primary + @ConditionalOnProperty.
 * 
 * Limitação: a blacklist é perdida se o servidor reiniciar.
 * Para uma única instância (como no Render free tier), isso é aceitável.
 */
@Service
@Primary
@ConditionalOnProperty(name = "aclp.redis.enabled", havingValue = "false", matchIfMissing = false)
public class InMemoryTokenBlacklistService implements TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTokenBlacklistService.class);

    // token -> expiration timestamp (ms)
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public InMemoryTokenBlacklistService() {
        logger.info("✅ Token blacklist IN-MEMORY ativada (Redis desabilitado)");
    }

    @Override
    public void blacklist(String token, long expirationMillis) {
        blacklist.put(token, expirationMillis);
        logger.debug("Token adicionado à blacklist in-memory (total: {})", blacklist.size());
    }

    @Override
    public boolean isBlacklisted(String token) {
        Long expiration = blacklist.get(token);
        if (expiration == null) {
            return false;
        }
        // Se já expirou, remove e retorna false
        if (System.currentTimeMillis() > expiration) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    /**
     * Limpa tokens expirados a cada 10 minutos para evitar memory leak.
     */
    @Scheduled(fixedRate = 600_000)
    public void limparTokensExpirados() {
        int antes = blacklist.size();
        long agora = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> agora > entry.getValue());
        int removidos = antes - blacklist.size();
        if (removidos > 0) {
            logger.debug("Blacklist cleanup: {} tokens expirados removidos (restantes: {})",
                    removidos, blacklist.size());
        }
    }
}
