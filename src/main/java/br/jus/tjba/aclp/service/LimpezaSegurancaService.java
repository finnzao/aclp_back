package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.repository.LoginAttemptRepository;
import br.jus.tjba.aclp.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class LimpezaSegurancaService {

    private final LoginAttemptRepository loginAttemptRepository;
    private final RateLimitFilter rateLimitFilter;

    @Value("${aclp.security.login-attempts.retention-days:90}")
    private int diasRetencao;

    /** 03h: fora do horário de expediente da vara e depois do cron de status (01h). */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void expurgarTentativasAntigas() {
        LocalDateTime limite = LocalDateTime.now().minusDays(diasRetencao);
        int removidas = loginAttemptRepository.deleteOldAttempts(limite);

        if (removidas > 0) {
            log.info("Tentativas de login expurgadas: {} (anteriores a {})", removidas, limite);
        }
    }

    /**
     * O mapa do rate limit precisa encolher sozinho — sob ataque distribuído um IP
     * novo por requisição faria o mapa crescer até estourar a heap.
     */
    @Scheduled(fixedDelay = 300_000)
    public void limparRateLimit() {
        rateLimitFilter.limparIpsOciosos();
        log.debug("Rate limit - IPs monitorados: {}", rateLimitFilter.getIpsMonitorados());
    }
}
