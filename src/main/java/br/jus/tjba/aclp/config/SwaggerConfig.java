package br.jus.tjba.aclp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local"),
                        new Server().url("https://api.tjba.jus.br").description("Servidor Produção")
                ))
                .info(new Info()
                        .title("ACLP - Sistema de Acompanhamento")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("TJBA - Suporte Técnico")
                                .email("suporte@tjba.jus.br")
                                .url("https://www.tjba.jus.br")));
    }
}