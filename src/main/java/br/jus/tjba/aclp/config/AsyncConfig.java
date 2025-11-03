package br.jus.tjba.aclp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuração para processamento assíncrono
 * Necessário para envio de emails de forma assíncrona (@Async)
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Pool mínimo de threads
        executor.setCorePoolSize(2);

        // Pool máximo de threads
        executor.setMaxPoolSize(5);

        // Capacidade da fila de espera
        executor.setQueueCapacity(100);

        // Prefixo para identificar threads nos logs
        executor.setThreadNamePrefix("async-email-");

        // Aguardar conclusão das tarefas antes de shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Timeout para aguardar conclusão (30 segundos)
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
}