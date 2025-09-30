package br.jus.tjba.aclp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * Configuração de Segurança DESABILITADA para testes
 *
 * ATENÇÃO: Esta configuração é INSEGURA
 * Use apenas em desenvolvimento/testes
 *
 * Para ativar: spring.profiles.active=dev
 */
@Configuration
@EnableWebSecurity
@Profile({"dev", "test"}) // Apenas em desenvolvimento
@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.warn("╔════════════════════════════════════════════════════════════════╗");
        log.warn("║  ⚠️  SEGURANÇA DESABILITADA - MODO DE TESTE                   ║");
        log.warn("║  Todos os endpoints estão ABERTOS sem autenticação            ║");
        log.warn("║  CSRF está DESABILITADO                                       ║");
        log.warn("║  NUNCA use esta configuração em PRODUÇÃO                      ║");
        log.warn("╚════════════════════════════════════════════════════════════════╝");

        http
                // Desabilitar CSRF (necessário para APIs REST)
                .csrf(csrf -> csrf.disable())

                // Configurar CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Configurar autorização - PERMITIR TUDO
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll() // Permitir TODAS as rotas
                        .anyRequest().permitAll()
                )

                // Stateless - não criar sessão
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Desabilitar formLogin
                .formLogin(form -> form.disable())

                // Desabilitar httpBasic
                .httpBasic(basic -> basic.disable())

                // Desabilitar logout padrão
                .logout(logout -> logout.disable());

        log.info("Security Filter Chain configurado:");
        log.info("- CSRF: DESABILITADO");
        log.info("- Autenticação: DESABILITADA");
        log.info("- CORS: PERMISSIVO");
        log.info("- Todas as rotas: PÚBLICAS");

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir todas as origens
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));

        // Permitir credenciais
        configuration.setAllowCredentials(true);

        // Permitir todos os métodos
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Permitir todos os headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Expor headers
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "Content-Type"
        ));

        // Cache preflight
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}