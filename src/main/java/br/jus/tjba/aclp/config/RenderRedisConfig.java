package br.jus.tjba.aclp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuração condicional do Redis.
 * 
 * Quando aclp.redis.enabled=true (padrão), usa o RedisConfig original.
 * Quando aclp.redis.enabled=false (Render), o Redis é completamente desabilitado
 * via spring.autoconfigure.exclude no application-render.properties e o
 * InMemoryTokenBlacklistService é usado no lugar.
 * 
 * Este arquivo NÃO precisa ser criado se o RedisConfig.java original já existe —
 * ele funciona quando Redis está disponível. O truque é que no profile "render",
 * o autoconfigure do Redis é excluído, então o RedisConfig original nem tenta
 * criar o bean (não há RedisConnectionFactory disponível).
 */
@Configuration
@ConditionalOnProperty(name = "aclp.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RenderRedisConfig {
    
    private static final Logger log = LoggerFactory.getLogger(RenderRedisConfig.class);
    
    /**
     * Este bean só é criado quando Redis está habilitado.
     * Quando aclp.redis.enabled=false, esta classe inteira é ignorada.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("✅ Redis habilitado - usando RedisTemplate");
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }
}
