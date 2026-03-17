package br.jus.tjba.aclp.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Self-Ping Scheduler — Evita que o Render free tier desligue o serviço.
 * 
 * O Render desliga web services gratuitos após 15 minutos sem tráfego.
 * Este scheduler faz GET no próprio health check a cada 5 minutos.
 * 
 * Controle via variáveis de ambiente:
 *   ACLP_SELF_PING_ENABLED=true
 *   ACLP_SELF_PING_URL=https://seu-app.onrender.com/api/setup/health
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
        this.restTemplate = new RestTemplate();

        if (enabled && selfUrl != null && !selfUrl.isBlank()) {
            log.info("✅ Self-Ping habilitado. URL: {}", selfUrl);
        } else if (enabled) {
            log.warn("⚠️  Self-Ping habilitado mas URL não configurada (ACLP_SELF_PING_URL)");
        } else {
            log.info("⏸️  Self-Ping desabilitado.");
        }
    }

    /**
     * Ping a cada 5 minutos (300.000 ms).
     * Delay inicial de 60 segundos para o app inicializar.
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void ping() {
        if (!enabled || selfUrl == null || selfUrl.isBlank()) {
            return;
        }

        try {
            String response = restTemplate.getForObject(selfUrl, String.class);
            log.debug("🏓 Self-Ping OK");
        } catch (Exception e) {
            log.warn("⚠️ Self-Ping falhou: {}", e.getMessage());
        }
    }
}
