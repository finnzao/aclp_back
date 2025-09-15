package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HistoricoEnderecoController.class)
@DisplayName("Testes do Controller de Histórico de Endereços")
class HistoricoEnderecoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HistoricoEnderecoService historicoEnderecoService;

    private HistoricoEnderecoDTO enderecoDTO;
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

        // Preparar DTO de endereço
        enderecoDTO = HistoricoEnderecoDTO.builder()
                .id(1L)
                .pessoaId(1L)
                .cep("40070-110")
                .logradouro("Avenida Sete de Setembro")
                .numero("1234")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .dataInicio(LocalDate.of(2024, 1, 1))
                .enderecoCompleto("Avenida Sete de Setembro, 1234, Centro, Salvador - BA, CEP: 40070-110")
                .enderecoResumido("Avenida Sete de Setembro, 1234, Salvador - BA")
                .enderecoAtivo(true)
                .build();
    }

    // ========== TESTES DE BUSCA DE HISTÓRICO ==========

    @Test
    @DisplayName("Deve buscar histórico por custodiado")
    void testBuscarHistoricoPorCustodiado() throws Exception {
        List<HistoricoEnderecoDTO> historicos = Arrays.asList(enderecoDTO);
        when(historicoEnderecoService.buscarHistoricoPorCustodiado(1L)).thenReturn(historicos);

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Histórico de endereços encontrado")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cidade", is("Salvador")));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar histórico com ID inválido")
    void testBuscarHistoricoIdInvalido() throws Exception {
        when(historicoEnderecoService.buscarHistoricoPorCustodiado(0L))
                .thenThrow(new IllegalArgumentException("ID da pessoa deve ser um número positivo"));

        mockMvc.perform(get("/api/historico-enderecos/pessoa/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("número positivo")));
    }

    // ========== TESTES DE ENDEREÇO ATIVO ==========

    @Test
    @DisplayName("Deve buscar endereço ativo")
    void testBuscarEnderecoAtivo() throws Exception {
        when(historicoEnderecoService.buscarEnderecoAtivo(1L))
                .thenReturn(Optional.of(enderecoDTO));

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/ativo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Endereço ativo encontrado")))
                .andExpect(jsonPath("$.data.enderecoAtivo", is(true)));
    }

    @Test
    @DisplayName("Deve retornar 404 quando não há endereço ativo")
    void testBuscarEnderecoAtivoNaoEncontrado() throws Exception {
        when(historicoEnderecoService.buscarEnderecoAtivo(1L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/ativo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não possui endereço ativo")));
    }

    // ========== TESTES DE ENDEREÇOS HISTÓRICOS ==========

    @Test
    @DisplayName("Deve buscar endereços históricos")
    void testBuscarEnderecosHistoricos() throws Exception {
        enderecoDTO.setEnderecoAtivo(false);
        enderecoDTO.setDataFim(LocalDate.of(2024, 6, 30));
        List<HistoricoEnderecoDTO> historicos = Arrays.asList(enderecoDTO);

        when(historicoEnderecoService.buscarEnderecosHistoricos(1L)).thenReturn(historicos);

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/historicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].enderecoAtivo", is(false)));
    }

    // ========== TESTES DE BUSCA POR PERÍODO ==========

    @Test
    @DisplayName("Deve buscar endereços por período")
    void testBuscarEnderecosPorPeriodo() throws Exception {
        List<HistoricoEnderecoDTO> enderecos = Arrays.asList(enderecoDTO);
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        LocalDate fim = LocalDate.of(2024, 12, 31);

        when(historicoEnderecoService.buscarEnderecosPorPeriodo(1L, inicio, fim))
                .thenReturn(enderecos);

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/periodo")
                        .param("inicio", "2024-01-01")
                        .param("fim", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Deve retornar erro quando período é inválido")
    void testBuscarEnderecosPeriodoInvalido() throws Exception {
        LocalDate inicio = LocalDate.of(2024, 12, 31);
        LocalDate fim = LocalDate.of(2024, 1, 1);

        when(historicoEnderecoService.buscarEnderecosPorPeriodo(1L, inicio, fim))
                .thenThrow(new IllegalArgumentException("Data de início não pode ser posterior à data de fim"));

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/periodo")
                        .param("inicio", "2024-12-31")
                        .param("fim", "2024-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    // ========== TESTES DE BUSCA POR LOCALIZAÇÃO ==========

    @Test
    @DisplayName("Deve buscar custodiados por cidade")
    void testBuscarCustodiadosPorCidade() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(historicoEnderecoService.buscarCustodiadosPorCidade("Salvador"))
                .thenReturn(custodiados);

        mockMvc.perform(get("/api/historico-enderecos/cidade/Salvador/pessoas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", is("João da Silva")));
    }

    @Test
    @DisplayName("Deve buscar custodiados por estado")
    void testBuscarCustodiadosPorEstado() throws Exception {
        List<Custodiado> custodiados = Arrays.asList(custodiado);
        when(historicoEnderecoService.buscarCustodiadosPorEstado("BA"))
                .thenReturn(custodiados);

        mockMvc.perform(get("/api/historico-enderecos/estado/BA/pessoas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ========== TESTES DE MUDANÇAS DE ENDEREÇO ==========

    @Test
    @DisplayName("Deve buscar mudanças por período")
    void testBuscarMudancasPorPeriodo() throws Exception {
        List<HistoricoEnderecoDTO> mudancas = Arrays.asList(enderecoDTO);
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        LocalDate fim = LocalDate.of(2024, 12, 31);

        when(historicoEnderecoService.buscarMudancasPorPeriodo(inicio, fim))
                .thenReturn(mudancas);

        mockMvc.perform(get("/api/historico-enderecos/mudancas/periodo")
                        .param("inicio", "2024-01-01")
                        .param("fim", "2024-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ========== TESTES DE CONTAGEM ==========

    @Test
    @DisplayName("Deve contar endereços por custodiado")
    void testContarEnderecosPorCustodiado() throws Exception {
        when(historicoEnderecoService.contarEnderecosPorCustodiado(1L))
                .thenReturn(3L);

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/total"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Total de endereços calculado")))
                .andExpect(jsonPath("$.data", is(3)));
    }

    // ========== TESTES DE BUSCA POR DATA ==========

    @Test
    @DisplayName("Deve buscar endereços ativos por data")
    void testBuscarEnderecosAtivosPorData() throws Exception {
        List<HistoricoEnderecoDTO> enderecos = Arrays.asList(enderecoDTO);
        LocalDate data = LocalDate.of(2024, 6, 15);

        when(historicoEnderecoService.buscarEnderecosAtivosPorData(data))
                .thenReturn(enderecos);

        mockMvc.perform(get("/api/historico-enderecos/data/2024-06-15/ativos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    // ========== TESTES DE BUSCA POR MOTIVO ==========

    @Test
    @DisplayName("Deve buscar endereços por motivo de alteração")
    void testBuscarPorMotivoAlteracao() throws Exception {
        enderecoDTO.setMotivoAlteracao("Mudança familiar");
        List<HistoricoEnderecoDTO> enderecos = Arrays.asList(enderecoDTO);

        when(historicoEnderecoService.buscarPorMotivoAlteracao("familiar"))
                .thenReturn(enderecos);

        mockMvc.perform(get("/api/historico-enderecos/motivo")
                        .param("motivo", "familiar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].motivoAlteracao", containsString("familiar")));
    }

    // ========== TESTES DE ÚLTIMO ENDEREÇO ANTERIOR ==========

    @Test
    @DisplayName("Deve buscar último endereço anterior a uma data")
    void testBuscarUltimoEnderecoAnterior() throws Exception {
        LocalDate data = LocalDate.of(2024, 6, 1);
        when(historicoEnderecoService.buscarUltimoEnderecoAnterior(1L, data))
                .thenReturn(Optional.of(enderecoDTO));

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/anterior")
                        .param("data", "2024-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Endereço anterior encontrado")))
                .andExpect(jsonPath("$.data.id", is(1)));
    }

    @Test
    @DisplayName("Deve retornar 404 quando não há endereço anterior")
    void testBuscarUltimoEnderecoAnteriorNaoEncontrado() throws Exception {
        LocalDate data = LocalDate.of(2024, 1, 1);
        when(historicoEnderecoService.buscarUltimoEnderecoAnterior(1L, data))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/historico-enderecos/pessoa/1/anterior")
                        .param("data", "2024-01-01"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("Nenhum endereço anterior")));
    }

    // ========== TESTES DE ESTATÍSTICAS ==========

    @Test
    @DisplayName("Deve buscar estatísticas de endereços")
    void testBuscarEstatisticas() throws Exception {
        HistoricoEnderecoService.EstatisticasEndereco estatistica =
                HistoricoEnderecoService.EstatisticasEndereco.builder()
                        .cidade("Salvador")
                        .estado("BA")
                        .totalCustodiados(50L)
                        .totalMudancas(120L)
                        .mediaDiasResidencia(180.5)
                        .build();

        List<HistoricoEnderecoService.EstatisticasEndereco> estatisticas = Arrays.asList(estatistica);
        when(historicoEnderecoService.buscarEstatisticasPorCidade()).thenReturn(estatisticas);

        mockMvc.perform(get("/api/historico-enderecos/estatisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Estatísticas calculadas com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cidade", is("Salvador")));
    }

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/historico-enderecos"))
                .andExpect(status().isOk());
    }
}