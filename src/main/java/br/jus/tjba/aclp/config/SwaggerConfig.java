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
        return new OpenAPI()
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local"),
                        new Server().url("https://api-aclp.tjba.jus.br").description("Servidor de Homologação"),
                        new Server().url("https://api.tjba.jus.br").description("Servidor Produção")
                ))
                .info(new Info()
                        .title("ACLP - Sistema de Acompanhamento de Comparecimento em Liberdade Provisória")
                        .version("2.0.0")
                        .description("""
                                Sistema completo para gerenciamento de pessoas em liberdade provisória, 
                                incluindo controle de comparecimentos, histórico de endereços e relatórios.
                                
                                ## Funcionalidades Principais:
                                
                                ### 👥 Gestão de Pessoas
                                - Cadastro completo com endereço obrigatório
                                - Validação de documentos (CPF/RG)
                                - Controle de periodicidade de comparecimentos
                                
                                ### 📍 Controle de Endereços
                                - Histórico completo de mudanças de endereço
                                - Validação de estados brasileiros
                                - Rastreabilidade temporal de residências
                                
                                ### ✅ Comparecimentos
                                - Registro presencial e online
                                - Controle automático de status (em conformidade/inadimplente)
                                - Histórico completo com observações
                                
                                ### 📊 Relatórios
                                - Estatísticas de comparecimentos
                                - Análise de mobilidade por região
                                - Relatórios por período
                                
                                ### 👨‍💼 Usuários
                                - Controle de acesso por perfil
                                - Auditoria de operações
                                """)
                        .contact(new Contact()
                                .name("TJBA - Tribunal de Justiça da Bahia")
                                .email("suporte.ti@tjba.jus.br")
                                .url("https://www.tjba.jus.br"))
                        .license(new License()
                                .name("Uso Interno TJBA")
                                .url("https://www.tjba.jus.br/politica-privacidade")))
                .tags(List.of(
                        new Tag().name("Pessoas").description("Gestão de pessoas em liberdade provisória"),
                        new Tag().name("Comparecimentos").description("Registro e controle de comparecimentos"),
                        new Tag().name("Histórico de Endereços").description("Gestão do histórico de endereços"),
                        new Tag().name("Usuários").description("Gestão de usuários do sistema"),
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
                                        .description("Status do comparecimento da pessoa")
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
                                        .example("BA"),

                                // Schema do ComparecimentoDTO
                                "ComparecimentoDTO", new ObjectSchema()
                                        .description("Dados para registro de comparecimento")
                                        .addProperty("pessoaId", new IntegerSchema()
                                                .description("ID da pessoa")
                                                .example(1))
                                        .addProperty("dataComparecimento", new DateSchema()
                                                .description("Data do comparecimento")
                                                .example("2024-09-05"))
                                        .addProperty("horaComparecimento", new StringSchema()
                                                .description("Horário do comparecimento (HH:mm)")
                                                .example("14:30"))
                                        .addProperty("tipoValidacao", new StringSchema()
                                                .description("Tipo de validação")
                                                ._enum(List.of("PRESENCIAL", "ONLINE"))
                                                .example("PRESENCIAL"))
                                        .addProperty("observacoes", new StringSchema()
                                                .description("Observações do comparecimento")
                                                .example("Comparecimento regular, pessoa em conformidade"))
                                        .addProperty("validadoPor", new StringSchema()
                                                .description("Nome do servidor responsável")
                                                .example("João Silva - Servidor TJBA"))
                                        .addProperty("mudancaEndereco", new BooleanSchema()
                                                .description("Indica se houve mudança de endereço")
                                                .example(false))
                                        .addProperty("motivoMudancaEndereco", new StringSchema()
                                                .description("Motivo da mudança de endereço")
                                                .example("Mudança por questões familiares"))
                                        .addProperty("novoEndereco", new Schema<>().$ref("#/components/schemas/EnderecoDTO")),

                                // Schema do EnderecoDTO
                                "EnderecoDTO", new ObjectSchema()
                                        .description("Dados do endereço")
                                        .addProperty("cep", new StringSchema()
                                                .description("CEP no formato 00000-000")
                                                .example("40070-110"))
                                        .addProperty("logradouro", new StringSchema()
                                                .description("Nome da rua, avenida, etc.")
                                                .example("Avenida Sete de Setembro"))
                                        .addProperty("numero", new StringSchema()
                                                .description("Número do endereço")
                                                .example("1234"))
                                        .addProperty("complemento", new StringSchema()
                                                .description("Complemento do endereço")
                                                .example("Apto 501"))
                                        .addProperty("bairro", new StringSchema()
                                                .description("Nome do bairro")
                                                .example("Centro"))
                                        .addProperty("cidade", new StringSchema()
                                                .description("Nome da cidade")
                                                .example("Salvador"))
                                        .addProperty("estado", new StringSchema()
                                                .description("Sigla do estado")
                                                .example("BA")),

                                // Schema de Estatísticas
                                "EstatisticasComparecimento", new ObjectSchema()
                                        .description("Estatísticas de comparecimentos")
                                        .addProperty("periodo", new StringSchema()
                                                .description("Período analisado")
                                                .example("2024-01-01 a 2024-12-31"))
                                        .addProperty("totalComparecimentos", new IntegerSchema()
                                                .description("Total de comparecimentos")
                                                .example(150))
                                        .addProperty("comparecimentosPresenciais", new IntegerSchema()
                                                .description("Comparecimentos presenciais")
                                                .example(120))
                                        .addProperty("comparecimentosOnline", new IntegerSchema()
                                                .description("Comparecimentos online")
                                                .example(25))
                                        .addProperty("cadastrosIniciais", new IntegerSchema()
                                                .description("Cadastros iniciais")
                                                .example(5))
                                        .addProperty("mudancasEndereco", new IntegerSchema()
                                                .description("Mudanças de endereço")
                                                .example(15))
                                        .addProperty("percentualPresencial", new Schema<>()
                                                .type("number")
                                                .format("double")
                                                .description("Percentual de comparecimentos presenciais")
                                                .example(80.0))
                                        .addProperty("percentualOnline", new Schema<>()
                                                .type("number")
                                                .format("double")
                                                .description("Percentual de comparecimentos online")
                                                .example(16.7)),

                                // Schema de exemplo para requisições
                                "ExemploComparecimentoPresencial", new ObjectSchema()
                                        .description("Exemplo de comparecimento presencial")
                                        .addProperty("pessoaId", new IntegerSchema().example(1))
                                        .addProperty("dataComparecimento", new DateSchema().example("2024-09-05"))
                                        .addProperty("horaComparecimento", new StringSchema().example("14:30"))
                                        .addProperty("tipoValidacao", new StringSchema().example("PRESENCIAL"))
                                        .addProperty("observacoes", new StringSchema().example("Comparecimento regular"))
                                        .addProperty("validadoPor", new StringSchema().example("Maria Santos - Servidor TJBA"))
                                        .addProperty("mudancaEndereco", new BooleanSchema().example(false)),

                                "ExemploComparecimentoComMudanca", new ObjectSchema()
                                        .description("Exemplo de comparecimento com mudança de endereço")
                                        .addProperty("pessoaId", new IntegerSchema().example(1))
                                        .addProperty("dataComparecimento", new DateSchema().example("2024-09-05"))
                                        .addProperty("horaComparecimento", new StringSchema().example("14:30"))
                                        .addProperty("tipoValidacao", new StringSchema().example("PRESENCIAL"))
                                        .addProperty("observacoes", new StringSchema().example("Comparecimento com mudança de endereço"))
                                        .addProperty("validadoPor", new StringSchema().example("João Silva - Servidor TJBA"))
                                        .addProperty("mudancaEndereco", new BooleanSchema().example(true))
                                        .addProperty("motivoMudancaEndereco", new StringSchema().example("Mudança por questões familiares"))
                                        .addProperty("novoEndereco", new ObjectSchema()
                                                .addProperty("cep", new StringSchema().example("41940-000"))
                                                .addProperty("logradouro", new StringSchema().example("Rua Nova Esperança"))
                                                .addProperty("numero", new StringSchema().example("789"))
                                                .addProperty("complemento", new StringSchema().example("Casa 2"))
                                                .addProperty("bairro", new StringSchema().example("Pituaçu"))
                                                .addProperty("cidade", new StringSchema().example("Salvador"))
                                                .addProperty("estado", new StringSchema().example("BA")))
                        )));
    }
}