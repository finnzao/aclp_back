package br.jus.tjba.aclp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração Web com interceptor de setup
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SetupInterceptor setupInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Adicionar interceptor de setup para todas as rotas
        registry.addInterceptor(setupInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // Recursos estáticos sempre permitidos
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/static/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/error",
                        // Health checks
                        "/actuator/health/**"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORREÇÃO: Usar allowedOriginPatterns ao invés de allowedOrigins com *
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:[*]",  // Permite qualquer porta no localhost
                        "http://127.0.0.1:[*]",  // Permite 127.0.0.1 com qualquer porta
                        "http://localhost:3000",  // Específico para o frontend
                        "http://localhost:3001"   // Porta alternativa se necessário
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders(
                        "Authorization",
                        "Content-Type",
                        "X-Total-Count",
                        "X-Request-ID"
                )
                .allowCredentials(true)  // Permite cookies e credenciais
                .maxAge(3600);

        // Configuração específica para setup
        registry.addMapping("/setup/**")
                .allowedOriginPatterns(
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]"
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        // Configuração para auth endpoints
        registry.addMapping("/auth/**")
                .allowedOriginPatterns(
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Type")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Recursos estáticos
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");

        // Swagger UI
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}