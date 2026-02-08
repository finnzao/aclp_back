package br.jus.tjba.aclp.config;

import br.jus.tjba.aclp.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> {})

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ==================== AUTENTICAÇÃO (público) ====================
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/auth/health").permitAll()
                        .requestMatchers("/api/auth/check-setup").permitAll()
                        .requestMatchers("/api/auth/validate").permitAll()

                        // ==================== PERFIL (autenticado) ====================
                        .requestMatchers("/api/auth/perfil").authenticated()
                        .requestMatchers("/api/auth/perfil/**").authenticated()

                        // ==================== SETUP (público, uso único) ====================
                        .requestMatchers("/api/setup/status").permitAll()
                        .requestMatchers("/api/setup/admin").permitAll()
                        .requestMatchers("/api/setup/health").permitAll()
                        .requestMatchers("/api/setup/audit").permitAll()

                        // ==================== CONVITES (público apenas validar/ativar) ====================
                        .requestMatchers("/api/usuarios/convites/validar/**").permitAll()
                        .requestMatchers("/api/usuarios/convites/ativar").permitAll()

                        // ==================== DOCUMENTAÇÃO ====================
                        .requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/swagger-ui.html").permitAll()
                        .requestMatchers("/swagger-resources/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()

                        // ==================== HEALTH CHECKS ====================
                        .requestMatchers("/actuator/health/**").permitAll()

                        // ==================== FRONTEND ====================
                        .requestMatchers("/", "/index.html", "/setup/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/static/**").permitAll()

                        // ==================== ADMINISTRATIVO ====================
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/usuarios/convites").hasRole("ADMIN")

                        // ==================== TUDO MAIS: AUTENTICADO ====================
                        .anyRequest().authenticated()
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(contentType -> {})
                        .xssProtection(xss -> {})
                        .cacheControl(cache -> {})
                );

        return http.build();
    }

    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}