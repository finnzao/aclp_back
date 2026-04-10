package br.jus.tjba.aclp.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Self-Ping Scheduler — Evita que o Render free tier desligue o serviço.
 *
 * CORREÇÃO DE PERFORMANCE: RestTemplate agora configurado com timeouts
 * para evitar que o thread do scheduler fique bloqueado indefinidamente.
 *
 * Timeouts:
 * - Connection timeout: 5 segundos
 * - Read timeout: 10 segundos
 */
@Component
public class SelfPingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SelfPingScheduler.class);

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String selfUrl;

    public SelfPingScheduler(
            @Value("${scc.self-ping.enabled:false}") boolean enabled,
            @Value("${scc.self-ping.url:}") String selfUrl) {
        this.enabled = enabled;
        this.selfUrl = selfUrl;

        // CORREÇÃO: Configurar RestTemplate com timeouts explícitos
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();

        if (enabled && selfUrl != null && !selfUrl.isBlank()) {
            log.info("Self-Ping habilitado. URL: {} (timeouts: connect=5s, read=10s)", selfUrl);
        } else if (enabled) {
            log.warn("Self-Ping habilitado mas URL não configurada");
        }
    }

    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void ping() {
        if (!enabled || selfUrl == null || selfUrl.isBlank()) return;
        try {
            restTemplate.getForObject(selfUrl, String.class);
            log.debug("Self-Ping OK");
        } catch (Exception e) {
            log.debug("Self-Ping falhou (esperado em cold start): {}", e.getMessage());
        }
    }
}
