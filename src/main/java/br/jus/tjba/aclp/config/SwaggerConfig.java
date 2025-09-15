package br.jus.tjba.aclp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.ArraySchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI components = new OpenAPI()
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local"),
                        new Server().url("https://api-aclp.tjba.jus.br").description("Servidor de Homologação"),
                        new Server().url("https://api.tjba.jus.br").description("Servidor Produção")
                ))
                .info(new Info()
                        .title("ACLP - Sistema de Acompanhamento de Comparecimento em Liberdade Provisória")
                        .version("2.0.0")
                        .description("""
                                Sistema completo para gerenciamento de custodiados em liberdade provisória, 
                                incluindo controle de comparecimentos, histórico de endereços e relatórios.
                                
                                ## Funcionalidades Principais:
                                
                                ### 👥 Gestão de Custodiados
                                - Cadastro completo com endereço obrigatório
                                - Validação de documentos (CPF/RG)
                                - Controle de periodicidade de comparecimentos
                                - Suporte a múltiplos custodiados por processo
                                
                                ### 📍 Controle de Endereços
                                - Histórico completo de mudanças de endereço
                                - Validação de estados brasileiros
                                - Rastreabilidade temporal de residências
                                
                                ### ✅ Comparecimentos
                                - Registro presencial e online
                                - Controle automático de status (em conformidade/inadimplente)
                                - Histórico completo com observações
                                - Mudança de endereço durante comparecimento
                                
                                ### 📊 Relatórios e Estatísticas
                                - Estatísticas de comparecimentos
                                - Análise de mobilidade por região
                                - Relatórios por período
                                - Análise de atrasos superiores a 30 dias
                                - Resumo completo do sistema
                                
                                ### 👨‍💼 Usuários e Segurança
                                - Controle de acesso por perfil (ADMIN/USUARIO)
                                - Verificação por email com código de segurança
                                - Setup inicial do sistema
                                - Auditoria de operações
                                
                                ### 🔧 Funcionalidades Especiais
                                - Verificação automática de inadimplentes
                                - Migração de cadastros iniciais
                                - Sistema de notificações por email
                                """)
                        .contact(new Contact()
                                .name("TJBA - Tribunal de Justiça da Bahia")
                                .email("suporte.ti@tjba.jus.br")
                                .url("https://www.tjba.jus.br"))
                        .license(new License()
                                .name("Uso Interno TJBA")
                                .url("https://www.tjba.jus.br/politica-privacidade")))
                .tags(List.of(
                        new Tag().name("Custodiados").description("Gestão de custodiados em liberdade provisória"),
                        new Tag().name("Comparecimentos").description("Registro e controle de comparecimentos"),
                        new Tag().name("Histórico de Endereços").description("Gestão do histórico de endereços dos custodiados"),
                        new Tag().name("Usuários").description("Gestão de usuários do sistema"),
                        new Tag().name("Verificação de Email").description("Sistema de verificação por email"),
                        new Tag().name("Setup").description("Configuração inicial do sistema"),
                        new Tag().name("Status").description("Gerenciamento automático de status"),
                        new Tag().name("Relatórios").description("Relatórios e estatísticas"),
                        new Tag().name("Sistema").description("Endpoints de teste e monitoramento")
                ))
                .components(new Components()
                        .schemas(Map.of(
                                // Schemas dos Enums
                                "TipoValidacao", new StringSchema()
                                        .description("Tipo de validação do comparecimento")
                                        ._enum(List.of("PRESENCIAL", "ONLINE", "CADASTRO_INICIAL"))
                                        .example("PRESENCIAL"),

                                "StatusComparecimento", new StringSchema()
                                        .description("Status do comparecimento do custodiado")
                                        ._enum(List.of("EM_CONFORMIDADE", "INADIMPLENTE"))
                                        .example("EM_CONFORMIDADE"),

                                "TipoUsuario", new StringSchema()
                                        .description("Tipo de usuário do sistema")
                                        ._enum(List.of("ADMIN", "USUARIO"))
                                        .example("USUARIO"),

                                "EstadoBrasil", new StringSchema()
                                        .description("Sigla do estado brasileiro")
                                        ._enum(List.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
                                                "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
                                                "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"))
                                        .example("BA")

                                // Schema do CustodiadoDTO

                                // Schema do ComparecimentoDTO

                                // Schema do EnderecoDTO

                                // Schema do UsuarioDTO

                                // Schema de Estatísticas

                                // Schema de Verificação de Email

                                // Schema do Setup
                        )));
        return components;
    }
}