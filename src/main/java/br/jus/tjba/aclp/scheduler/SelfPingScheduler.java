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
 * Self-Ping Scheduler — mantém o serviço acordado no Render durante
 * o horário comercial (segunda a sexta, 08:00 às 13:55, fuso America/Bahia).
 */
@Component
public class SelfPingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SelfPingScheduler.class);

    private final RestTemplate restTemplate;
    private final boolean enabled;
    private final String selfUrl;

    public SelfPingScheduler(
            @Value("${aclp.self-ping.enabled:false}") boolean enabled,
            @Value("${aclp.self-ping.url:}") String selfUrl) {
        this.enabled = enabled;
        this.selfUrl = selfUrl;

        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();

        if (enabled && selfUrl != null && !selfUrl.isBlank()) {
            log.info("Self-Ping habilitado. URL: {} | Janela: seg-sex 08:00-14:00 (America/Bahia)", selfUrl);
        } else if (enabled) {
            log.warn("Self-Ping habilitado mas URL não configurada");
        }
    }

    @Scheduled(cron = "0 0/5 8-13 * * MON-FRI", zone = "America/Bahia")
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
