package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.dto.UsuarioDTO;
import br.jus.tjba.aclp.model.enums.TipoUsuario;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // driver-class-name junto da URL: application.properties fixa o driver do
        // PostgreSQL e ele venceria só a troca de URL, deixando o dialect insolúvel.
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "aclp.email.enabled=false"
})
// Escrito antes da autenticacao existir: chamava tudo anonimo e, com o
// SecurityConfig real carregado (.anyRequest().authenticated()), todo endpoint
// respondia 403. ADMIN porque o fluxo passa por /api/usuarios, que exige ADMIN.
// CSRF esta ativo: toda requisicao mutavel leva .with(csrf()).
@WithMockUser(roles = {"ADMIN", "USUARIO"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Teste de Integração Completo do Sistema ACLP")
class AclpIntegrationTest {

    // Sem @Transactional nos metodos: esta classe e um fluxo encadeado (@Order +
    // campos static com os IDs). O rollback por teste apagava o que o passo
    // seguinte precisava encontrar.

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String custodiadoPublicId;
    private static Long custodiadoId;
    private static Long usuarioId;
    private static Long comparecimentoId;

    // ========== TESTE DE HEALTH CHECK ==========

    @Test
    @Order(1)
    @DisplayName("Sistema deve estar funcionando")
    void testSystemHealth() throws Exception {
        mockMvc.perform(get("/api/test/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("funcionando")));
    }

    // ========== TESTES DE USUÁRIOS ==========

    @Test
    @Order(2)
    @DisplayName("Deve criar um usuário administrador")
    void testCreateAdminUser() throws Exception {
        UsuarioDTO admin = UsuarioDTO.builder()
                .nome("Admin Sistema")
                .email("admin@tjba.jus.br")
                .senha("Admin@2024!")
                .tipo(TipoUsuario.ADMIN)
                .departamento("TI")
                .build();

        MvcResult result = mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.nome", is("Admin Sistema")))
                .andExpect(jsonPath("$.data.tipo", is("ADMIN")))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        usuarioId = objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @Order(3)
    @DisplayName("Deve listar usuários criados")
    void testListUsers() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));
    }

    // ========== TESTES DE CUSTODIADOS ==========

    @Test
    @Order(4)
    @DisplayName("Deve criar um custodiado com endereço completo")
    void testCreateCustodiado() throws Exception {
        CustodiadoDTO custodiado = CustodiadoDTO.builder()
                .nome("João Pedro Silva")
                .cpf("529.982.247-25")
                .rg("1234567")
                .contato("(71) 98765-4321")
                .processo("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.now().minusMonths(2))
                .periodicidade(30)
                // Relativa ao hoje: data fixa deixaria o proximo comparecimento no
                // passado e o custodiado nasceria INADIMPLENTE.
                .dataComparecimentoInicial(LocalDate.now().minusDays(5))
                .observacoes("Custodiado em liberdade provisória")
                // Endereço obrigatório
                .cep("40070-110")
                .logradouro("Avenida Sete de Setembro")
                .numero("1234")
                .complemento("Apto 501")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .build();

        MvcResult result = mockMvc.perform(post("/api/custodiados")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiado)))
                                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                // O servico normaliza o nome para maiusculas ao salvar.
                .andExpect(jsonPath("$.data.nome", is("JOÃO PEDRO SILVA")))
                .andExpect(jsonPath("$.data.processo", is("0000001-00.2024.8.05.0001")))
                .andExpect(jsonPath("$.data.status", is("EM_CONFORMIDADE")))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        // CustodiadoResponseDTO.id passou a ser o publicId (UUID em String).
        custodiadoPublicId = objectMapper.readTree(response).path("data").path("id").asText();
    }

    @Test
    @Order(5)
    @DisplayName("Deve buscar custodiado criado")
    void testGetCustodiado() throws Exception {
        mockMvc.perform(get("/api/custodiados/" + custodiadoPublicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(custodiadoPublicId)))
                .andExpect(jsonPath("$.data.nome", is("JOÃO PEDRO SILVA")));

        // O id numerico nao vem em CustodiadoResponseDTO, mas os endpoints de
        // comparecimento e endereco ainda o exigem. A listagem expoe numericId.
        MvcResult lista = mockMvc.perform(get("/api/custodiados"))
                .andExpect(status().isOk())
                .andReturn();
        custodiadoId = objectMapper.readTree(lista.getResponse().getContentAsString())
                .path("data").get(0).path("numericId").asLong();
    }

    // Removido testGetCustodiadoByProcesso: GET /api/custodiados/processo/{n} nao
    // existe mais — a busca por numero de processo esta em ProcessoController
    // (GET /api/processos/numero/{numero}).

    // ========== TESTES DE COMPARECIMENTOS ==========

    @Test
    @Order(7)
    @DisplayName("Deve registrar comparecimento presencial")
    void testRegisterComparecimento() throws Exception {
        ComparecimentoDTO comparecimento = ComparecimentoDTO.builder()
                .custodiadoId(custodiadoId)
                .dataComparecimento(LocalDate.now())
                .horaComparecimento(LocalTime.of(14, 30))
                .tipoValidacao(TipoValidacao.PRESENCIAL)
                .validadoPor("Maria Santos - Servidora TJBA")
                .observacoes("Comparecimento regular, custodiado em conformidade")
                .mudancaEndereco(false)
                .build();

        MvcResult result = mockMvc.perform(post("/api/comparecimentos/registrar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comparecimento)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Comparecimento registrado com sucesso")))
                .andExpect(jsonPath("$.data.custodiadoId", is(custodiadoId.intValue())))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        comparecimentoId = objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    @Order(8)
    @DisplayName("Deve buscar histórico de comparecimentos")
    void testGetHistoricoComparecimentos() throws Exception {
        mockMvc.perform(get("/api/comparecimentos/custodiado/" + custodiadoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));
    }

    @Test
    @Order(9)
    @DisplayName("Deve registrar comparecimento com mudança de endereço")
    void testRegisterComparecimentoComMudancaEndereco() throws Exception {
        ComparecimentoDTO.EnderecoDTO novoEndereco = ComparecimentoDTO.EnderecoDTO.builder()
                .cep("41940-000")
                .logradouro("Rua Nova Esperança")
                .numero("789")
                .bairro("Pituaçu")
                .cidade("Salvador")
                .estado("BA")
                .build();

        ComparecimentoDTO comparecimento = ComparecimentoDTO.builder()
                .custodiadoId(custodiadoId)
                .dataComparecimento(LocalDate.now().plusDays(30))
                .horaComparecimento(LocalTime.of(10, 0))
                .tipoValidacao(TipoValidacao.PRESENCIAL)
                .validadoPor("João Silva - Servidor TJBA")
                .observacoes("Comparecimento com mudança de endereço")
                .mudancaEndereco(true)
                .motivoMudancaEndereco("Mudança por questões familiares")
                .novoEndereco(novoEndereco)
                .build();

        mockMvc.perform(post("/api/comparecimentos/registrar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comparecimento)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.mudancaEndereco", is(true)));
    }

    // ========== TESTES DE HISTÓRICO DE ENDEREÇOS ==========

    @Test
    @Order(10)
    @DisplayName("Deve buscar histórico de endereços do custodiado")
    void testGetHistoricoEnderecos() throws Exception {
        mockMvc.perform(get("/api/historico-enderecos/pessoa/" + custodiadoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));
    }

    @Test
    @Order(11)
    @DisplayName("Deve buscar endereço ativo do custodiado")
    void testGetEnderecoAtivo() throws Exception {
        mockMvc.perform(get("/api/historico-enderecos/pessoa/" + custodiadoId + "/ativo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.enderecoAtivo", is(true)));
    }

    // ========== TESTES DE STATUS ==========

    @Test
    @Order(12)
    @DisplayName("Deve verificar inadimplentes")
    void testVerificarInadimplentes() throws Exception {
        mockMvc.perform(post("/api/status/verificar-inadimplentes")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.pessoasMarcadas", isA(Number.class)));
    }

    @Test
    @Order(13)
    @DisplayName("Deve obter estatísticas de status")
    void testGetEstatisticasStatus() throws Exception {
        mockMvc.perform(get("/api/status/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalCustodiados", greaterThanOrEqualTo(1)));
    }

    // ========== TESTES DE ESTATÍSTICAS GERAIS ==========

    @Test
    @Order(14)
    @DisplayName("Deve buscar estatísticas gerais de comparecimentos")
    void testGetEstatisticasGerais() throws Exception {
        mockMvc.perform(get("/api/comparecimentos/estatisticas/geral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalComparecimentos", greaterThanOrEqualTo(0)));
    }

    @Test
    @Order(15)
    @DisplayName("Deve buscar resumo completo do sistema")
    void testGetResumoSistema() throws Exception {
        mockMvc.perform(get("/api/comparecimentos/resumo/sistema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalCustodiados", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.analiseAtrasos", notNullValue()));
    }

    // ========== TESTES DE BUSCA ==========

    @Test
    @Order(16)
    @DisplayName("Deve buscar custodiados por termo")
    void testBuscarCustodiados() throws Exception {
        mockMvc.perform(get("/api/custodiados/buscar")
                        .param("termo", "João"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(17)
    @DisplayName("Deve buscar custodiados inadimplentes")
    void testGetCustodiadosInadimplentes() throws Exception {
        mockMvc.perform(get("/api/custodiados/inadimplentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", isA(List.class)));
    }

    // ========== TESTES DE VALIDAÇÃO ==========

    @Test
    @Order(18)
    @DisplayName("Não deve criar custodiado com CPF duplicado")
    void testCreateCustodiadoDuplicateCPF() throws Exception {
        CustodiadoDTO custodiado = CustodiadoDTO.builder()
                .nome("Outro Nome")
                .cpf("529.982.247-25") // CPF já cadastrado
                .contato("(71) 99999-9999")
                .processo("0000002-00.2024.8.05.0001")
                .vara("2ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.of(2024, 1, 20))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.of(2024, 2, 15))
                .cep("40070-110")
                .logradouro("Rua Teste")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .build();

        mockMvc.perform(post("/api/custodiados")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("CPF")));
    }

    @Test
    @Order(19)
    @DisplayName("Não deve criar usuário com email duplicado")
    void testCreateUserDuplicateEmail() throws Exception {
        UsuarioDTO usuario = UsuarioDTO.builder()
                .nome("Outro Usuário")
                .email("admin@tjba.jus.br") // Email já cadastrado
                .senha("Senha@123")
                .tipo(TipoUsuario.USUARIO)
                .build();

        mockMvc.perform(post("/api/usuarios")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(usuario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Email")));
    }

    // ========== TESTE DE LIMPEZA ==========

    @Test
    @Order(20)
    @DisplayName("Deve limpar dados de teste")
    void testCleanup() throws Exception {
        // Este teste não faz nada, mas garante que o @Transactional
        // reverterá todas as mudanças após a execução dos testes
        assert true;
    }
}