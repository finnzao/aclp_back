package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ComparecimentoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoComparecimento;
import br.jus.tjba.aclp.model.enums.TipoValidacao;
import br.jus.tjba.aclp.service.ComparecimentoService;
import br.jus.tjba.aclp.service.StatusSchedulerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComparecimentoController.class)
@DisplayName("Testes do Controller de Comparecimentos")
class ComparecimentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ComparecimentoService comparecimentoService;

    @MockBean
    private StatusSchedulerService statusSchedulerService;

    @Autowired
    private ObjectMapper objectMapper;

    private HistoricoComparecimento historico;
    private ComparecimentoDTO comparecimentoDTO;
    private Custodiado custodiado;

    @BeforeEach
    void setUp() {
        // Preparar custodiado
        custodiado = Custodiado.builder()
                .id(1L)
                .nome("João da Silva")
                .cpf("123.456.789-00")
                .processo("0000001-00.2024.8.05.0001")
                .build();

        // Preparar histórico de comparecimento
        historico = HistoricoComparecimento.builder()
                .id(1L)
                .custodiado(custodiado)
                .dataComparecimento(LocalDate.of(2024, 9, 5))
                .horaComparecimento(LocalTime.of(14, 30))
                .tipoValidacao(TipoValidacao.PRESENCIAL)
                .validadoPor("João Silva - Servidor TJBA")
                .observacoes("Comparecimento regular")
                .mudancaEndereco(false)
                .build();

        // Preparar DTO
        comparecimentoDTO = ComparecimentoDTO.builder()
                .custodiadoId(1L)
                .dataComparecimento(LocalDate.of(2024, 9, 5))
                .horaComparecimento(LocalTime.of(14, 30))
                .tipoValidacao(TipoValidacao.PRESENCIAL)
                .validadoPor("João Silva - Servidor TJBA")
                .observacoes("Comparecimento regular")
                .mudancaEndereco(false)
                .build();
    }

    // ========== TESTES DE REGISTRO DE COMPARECIMENTO ==========

    @Test
    @DisplayName("Deve registrar comparecimento com sucesso")
    void testRegistrarComparecimento() throws Exception {
        when(comparecimentoService.registrarComparecimento(any(ComparecimentoDTO.class)))
                .thenReturn(historico);

        mockMvc.perform(post("/api/comparecimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comparecimentoDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Comparecimento registrado com sucesso")))
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.custodiadoId", is(1)));
    }

    @Test
    @DisplayName("Deve registrar comparecimento com mudança de endereço")
    void testRegistrarComparecimentoComMudancaEndereco() throws Exception {
        ComparecimentoDTO.EnderecoDTO novoEndereco = ComparecimentoDTO.EnderecoDTO.builder()
                .cep("40070-110")
                .logradouro("Avenida Sete de Setembro")
                .numero("1234")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .build();

        comparecimentoDTO.setMudancaEndereco(true);
        comparecimentoDTO.setMotivoMudancaEndereco("Mudança por questões familiares");
        comparecimentoDTO.setNovoEndereco(novoEndereco);

        historico.setMudancaEndereco(true);
        historico.setMotivoMudancaEndereco("Mudança por questões familiares");

        when(comparecimentoService.registrarComparecimento(any(ComparecimentoDTO.class)))
                .thenReturn(historico);

        mockMvc.perform(post("/api/comparecimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comparecimentoDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.mudancaEndereco", is(true)));
    }

    @Test
    @DisplayName("Deve retornar erro ao registrar comparecimento com dados inválidos")
    void testRegistrarComparecimentoInvalido() throws Exception {
        ComparecimentoDTO invalidDTO = new ComparecimentoDTO();
        // DTO sem campos obrigatórios

        mockMvc.perform(post("/api/comparecimentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isBadRequest());
    }

    // ========== TESTES DE BUSCA DE HISTÓRICO ==========

    @Test
    @DisplayName("Deve buscar histórico por custodiado")
    void testBuscarHistoricoPorCustodiado() throws Exception {
        List<HistoricoComparecimento> historicos = Arrays.asList(historico);
        when(comparecimentoService.buscarHistoricoPorCustodiado(1L)).thenReturn(historicos);

        mockMvc.perform(get("/api/comparecimentos/custodiado/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Histórico de comparecimentos encontrado")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(1)));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar histórico com ID inválido")
    void testBuscarHistoricoIdInvalido() throws Exception {
        when(comparecimentoService.buscarHistoricoPorCustodiado(0L))
                .thenThrow(new IllegalArgumentException("ID do custodiado deve ser um número positivo"));

        mockMvc.perform(get("/api/comparecimentos/custodiado/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("número positivo")));
    }

    // ========== TESTES DE BUSCA POR PERÍODO ==========

    @Test
    @DisplayName("Deve buscar comparecimentos por período")
    void testBuscarComparecimentosPorPeriodo() throws Exception {
        List<HistoricoComparecimento> historicos = Arrays.asList(historico);
        LocalDate inicio = LocalDate.of(2024, 9, 1);
        LocalDate fim = LocalDate.of(2024, 9, 30);

        when(comparecimentoService.buscarComparecimentosPorPeriodo(inicio, fim))
                .thenReturn(historicos);

        mockMvc.perform(get("/api/comparecimentos/periodo")
                        .param("inicio", "2024-09-01")
                        .param("fim", "2024-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Deve buscar comparecimentos de hoje")
    void testBuscarComparecimentosHoje() throws Exception {
        List<HistoricoComparecimento> historicos = Arrays.asList(historico);
        when(comparecimentoService.buscarComparecimentosHoje()).thenReturn(historicos);

        mockMvc.perform(get("/api/comparecimentos/hoje"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Comparecimentos de hoje encontrados")))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ========== TESTES DE MUDANÇA DE ENDEREÇO ==========

    @Test
    @DisplayName("Deve buscar comparecimentos com mudança de endereço")
    void testBuscarComparecimentosComMudancaEndereco() throws Exception {
        historico.setMudancaEndereco(true);
        List<HistoricoComparecimento> historicos = Arrays.asList(historico);

        when(comparecimentoService.buscarComparecimentosComMudancaEndereco(1L))
                .thenReturn(historicos);

        mockMvc.perform(get("/api/comparecimentos/custodiado/1/mudancas-endereco"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].mudancaEndereco", is(true)));
    }

    // ========== TESTES DE ATUALIZAÇÃO ==========

    @Test
    @DisplayName("Deve atualizar observações do comparecimento")
    void testAtualizarObservacoes() throws Exception {
        String novasObservacoes = "Observações atualizadas";
        historico.setObservacoes(novasObservacoes);

        when(comparecimentoService.atualizarObservacoes(1L, novasObservacoes))
                .thenReturn(historico);

        mockMvc.perform(put("/api/comparecimentos/1/observacoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(novasObservacoes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Observações atualizadas com sucesso")))
                .andExpect(jsonPath("$.data.observacoes", is(novasObservacoes)));
    }

    // ========== TESTES DE VERIFICAÇÃO DE INADIMPLENTES ==========

    @Test
    @DisplayName("Deve verificar inadimplentes")
    void testVerificarInadimplentes() throws Exception {
        when(statusSchedulerService.verificarStatusManual()).thenReturn(3L);

        mockMvc.perform(post("/api/comparecimentos/verificar-inadimplentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("3 custodiado(s)")))
                .andExpect(jsonPath("$.data.custodiadosMarcados", is(3)));
    }

    @Test
    @DisplayName("Deve retornar mensagem quando nenhum inadimplente")
    void testVerificarInadimplentesNenhum() throws Exception {
        when(statusSchedulerService.verificarStatusManual()).thenReturn(0L);

        mockMvc.perform(post("/api/comparecimentos/verificar-inadimplentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Nenhum custodiado")))
                .andExpect(jsonPath("$.data.custodiadosMarcados", is(0)));
    }

    // ========== TESTES DE ESTATÍSTICAS ==========

    @Test
    @DisplayName("Deve buscar estatísticas por período")
    void testBuscarEstatisticas() throws Exception {
        ComparecimentoService.EstatisticasComparecimento estatisticas =
                ComparecimentoService.EstatisticasComparecimento.builder()
                        .periodo("2024-09-01 a 2024-09-30")
                        .totalComparecimentos(50)
                        .comparecimentosPresenciais(40)
                        .comparecimentosOnline(10)
                        .percentualPresencial(80.0)
                        .percentualOnline(20.0)
                        .build();

        LocalDate inicio = LocalDate.of(2024, 9, 1);
        LocalDate fim = LocalDate.of(2024, 9, 30);

        when(comparecimentoService.buscarEstatisticas(inicio, fim))
                .thenReturn(estatisticas);

        mockMvc.perform(get("/api/comparecimentos/estatisticas")
                        .param("inicio", "2024-09-01")
                        .param("fim", "2024-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalComparecimentos", is(50)))
                .andExpect(jsonPath("$.data.percentualPresencial", is(80.0)));
    }

    @Test
    @DisplayName("Deve buscar estatísticas gerais")
    void testBuscarEstatisticasGerais() throws Exception {
        ComparecimentoService.EstatisticasGerais estatisticas =
                ComparecimentoService.EstatisticasGerais.builder()
                        .totalComparecimentos(1000)
                        .comparecimentosPresenciais(800)
                        .comparecimentosOnline(200)
                        .comparecimentosHoje(10)
                        .comparecimentosEsteMes(150)
                        .build();

        when(comparecimentoService.buscarEstatisticasGerais())
                .thenReturn(estatisticas);

        mockMvc.perform(get("/api/comparecimentos/estatisticas/geral"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalComparecimentos", is(1000)));
    }

    @Test
    @DisplayName("Deve buscar resumo do sistema")
    void testBuscarResumoSistema() throws Exception {
        ComparecimentoService.ResumoSistema resumo =
                ComparecimentoService.ResumoSistema.builder()
                        .totalCustodiados(100)
                        .custodiadosEmConformidade(80)
                        .custodiadosInadimplentes(20)
                        .comparecimentosHoje(5)
                        .totalComparecimentos(1500)
                        .build();

        when(comparecimentoService.buscarResumoSistema())
                .thenReturn(resumo);

        mockMvc.perform(get("/api/comparecimentos/resumo/sistema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalCustodiados", is(100)));
    }

    // ========== TESTES DE MIGRAÇÃO ==========

    @Test
    @DisplayName("Deve migrar cadastros iniciais")
    void testMigrarCadastrosIniciais() throws Exception {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("status", "success");
        resultado.put("totalCustodiados", 10);
        resultado.put("custodiadosMigrados", 8);

        when(comparecimentoService.migrarCadastrosIniciais("Sistema ACLP"))
                .thenReturn(resultado);

        mockMvc.perform(post("/api/comparecimentos/migrar/cadastros-iniciais")
                        .param("validadoPor", "Sistema ACLP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.custodiadosMigrados", is(8)));
    }

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/comparecimentos"))
                .andExpect(status().isOk());
    }
}