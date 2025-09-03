package br.jus.tjba.aclp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
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
                        new Server().url("http://localhost:8080").description("Servidor de Desenvolvimento")
                ))
                .info(new Info()
                        .title("ACLP - API Rest")
                        .version("1.0.0")
                        .description("Sistema de Acompanhamento de Comparecimento de Liberdade Provisória - TJBA")
                        .contact(new Contact()
                                .name("Tribunal de Justiça da Bahia")
                                .email("suporte@tjba.jus.br")
                                .url("https://www.tjba.jus.br"))
                        .license(new License()
                                .name("TJBA")
                                .url("https://www.tjba.jus.br")));
    }
}