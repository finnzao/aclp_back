package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.HistoricoEnderecoDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
// Sem filtros de seguranca: o slice @WebMvcTest nao carrega o SecurityConfig da
// aplicacao (usa o chain padrao do Boot), entao autenticar aqui nao testaria a
// seguranca real — so obrigaria @WithMockUser e csrf() em cada metodo por causa de
// um chain que nem e o de producao. A seguranca de verdade e coberta por
// SegurancaOwaspTest, que sobe o contexto completo. @PreAuthorize continua ativo.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Testes do Controller de Histórico de Endereços")
class HistoricoEnderecoControllerTest extends WebMvcSliceSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HistoricoEnderecoService historicoEnderecoService;

    private HistoricoEnderecoDTO enderecoDTO;
    private PessoaMonitorada custodiado;

    @BeforeEach
    void setUp() {
        // Preparar custodiado
        custodiado = PessoaMonitorada.builder()
                .id(1L)
                .nome("João da Silva")
                .cpf("123.456.789-00")
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
                .andExpect(jsonPath("$.message", notNullValue()))
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

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/historico-enderecos"))
                .andExpect(status().isOk());
    }
}