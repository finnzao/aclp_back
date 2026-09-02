package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.PessoaMonitoradaDTO;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.PessoaMonitoradaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PessoaMonitoradaController.class)
// Sem filtros de seguranca: o slice @WebMvcTest nao carrega o SecurityConfig da
// aplicacao (usa o chain padrao do Boot), entao autenticar aqui nao testaria a
// seguranca real — so obrigaria @WithMockUser e csrf() em cada metodo por causa de
// um chain que nem e o de producao. A seguranca de verdade e coberta por
// SegurancaOwaspTest, que sobe o contexto completo. @PreAuthorize continua ativo.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Testes do Controller de Pessoas monitoradas")
class PessoaMonitoradaControllerTest extends WebMvcSliceSupport {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PessoaMonitoradaService custodiadoService;

    // PessoaMonitoradaController injeta este serviço no construtor — específico deste
    // controller, por isso fica aqui e não na base compartilhada.
    @MockBean
    private br.jus.tjba.aclp.service.HistoricoEnderecoService historicoEnderecoService;

    @Autowired
    private ObjectMapper objectMapper;

    // O controller identifica o custodiado por publicId (UUID), nao pelo id numerico.
    private static final java.util.UUID PUBLIC_ID =
            java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    private PessoaMonitorada custodiado;
    private PessoaMonitoradaDTO custodiadoDTO;

    @BeforeEach
    void setUp() {
        // Preparar custodiado de teste
        custodiado = PessoaMonitorada.builder()
                .id(1L)
                .publicId(PUBLIC_ID)
                .nome("João da Silva")
                .cpf("123.456.789-00")
                .rg("1234567")
                .contato("(71) 99999-9999")
                .processo("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.of(2024, 1, 15))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.of(2024, 2, 1))
                .status(StatusComparecimento.EM_CONFORMIDADE)
                .ultimoComparecimento(LocalDate.now().minusDays(10))
                .proximoComparecimento(LocalDate.now().plusDays(20))
                .observacoes("Pessoa monitorada em conformidade")
                .build();

        // Preparar DTO
        custodiadoDTO = PessoaMonitoradaDTO.builder()
                .nome("João da Silva")
                .cpf("123.456.789-00")
                .rg("1234567")
                .contato("(71) 99999-9999")
                .processo("0000001-00.2024.8.05.0001")
                .vara("1ª Vara Criminal")
                .comarca("Salvador")
                .dataDecisao(LocalDate.of(2024, 1, 15))
                .periodicidade(30)
                .dataComparecimentoInicial(LocalDate.of(2024, 2, 1))
                .observacoes("Pessoa monitorada em conformidade")
                .cep("40070-110")
                .logradouro("Avenida Sete de Setembro")
                .numero("1234")
                .bairro("Centro")
                .cidade("Salvador")
                .estado("BA")
                .build();
    }

    // ========== TESTES DE LISTAGEM ==========

    // Removidos: testFindByProcesso e testCountByProcesso.
    // As rotas GET /api/pessoas-monitoradas/processo/{n} e .../count nao existem mais em
    // nenhum controller — a busca por numero de processo migrou para
    // ProcessoController (GET /api/processos/numero/{numero}). Os testes exercitavam
    // endpoints inexistentes e por isso respondiam 500.

    @Test
    @DisplayName("Deve listar todos os custodiados")
    void testFindAll() throws Exception {
        when(custodiadoService.listarTodosAtivosDTO())
                .thenReturn(List.of(br.jus.tjba.aclp.dto.PessoaMonitoradaListDTO.fromEntity(custodiado)));
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Pessoas monitoradas listadas com sucesso")))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", is("João da Silva")))
                .andExpect(jsonPath("$.data[0].processo", is("0000001-00.2024.8.05.0001")));
    }

    @Test
    @DisplayName("Deve buscar custodiado por ID")
    void testFindById() throws Exception {
        when(custodiadoService.findByPublicId(PUBLIC_ID.toString())).thenReturn(Optional.of(custodiado));
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas/" + PUBLIC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Pessoa monitorada encontrada")))
                // O DTO expoe id = publicId (UUID) e numericId = id interno.
                .andExpect(jsonPath("$.data.id", is(PUBLIC_ID.toString())))
                .andExpect(jsonPath("$.data.nome", is("João da Silva")));
    }

    @Test
    @DisplayName("Deve retornar 404 quando custodiado não encontrado")
    void testFindByIdNotFound() throws Exception {
        when(custodiadoService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/pessoas-monitoradas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("não encontrado")));
    }

    // ========== TESTES DE CRIAÇÃO ==========

    @Test
    @DisplayName("Deve criar novo custodiado")
    void testSave() throws Exception {
        when(custodiadoService.save(any(PessoaMonitoradaDTO.class))).thenReturn(custodiado);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(post("/api/pessoas-monitoradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Pessoa monitorada cadastrada")))
                .andExpect(jsonPath("$.data.nome", is("João da Silva")));
    }

    @Test
    @DisplayName("Deve retornar erro ao criar custodiado com dados inválidos")
    void testSaveInvalid() throws Exception {
        PessoaMonitoradaDTO invalidDTO = new PessoaMonitoradaDTO();
        // DTO sem campos obrigatórios

        mockMvc.perform(post("/api/pessoas-monitoradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar erro ao criar custodiado com CPF duplicado")
    void testSaveDuplicateCPF() throws Exception {
        when(custodiadoService.save(any(PessoaMonitoradaDTO.class)))
                .thenThrow(new IllegalArgumentException("CPF já está cadastrado no sistema"));

        mockMvc.perform(post("/api/pessoas-monitoradas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("CPF já está cadastrado")));
    }

    // ========== TESTES DE ATUALIZAÇÃO ==========

    @Test
    @DisplayName("Deve atualizar custodiado")
    void testUpdate() throws Exception {
        when(custodiadoService.updateByPublicId(eq(PUBLIC_ID.toString()), any(PessoaMonitoradaDTO.class))).thenReturn(custodiado);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(put("/api/pessoas-monitoradas/" + PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custodiadoDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Pessoa monitorada atualizada")));
    }

    // ========== TESTES DE EXCLUSÃO ==========

    @Test
    @DisplayName("Deve excluir custodiado")
    void testDelete() throws Exception {
        doNothing().when(custodiadoService).delete(1L);

        mockMvc.perform(delete("/api/pessoas-monitoradas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", is("Pessoa monitorada arquivada com sucesso")));
    }

    // ========== TESTES DE BUSCA POR STATUS ==========

    @Test
    @DisplayName("Deve buscar custodiados por status")
    void testFindByStatus() throws Exception {
        List<PessoaMonitorada> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findByStatus(StatusComparecimento.EM_CONFORMIDADE)).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas/status/EM_CONFORMIDADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("EM_CONFORMIDADE")));
    }

    @Test
    @DisplayName("Deve buscar custodiados com comparecimento hoje")
    void testFindComparecimentosHoje() throws Exception {
        List<PessoaMonitorada> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findComparecimentosHoje()).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas/comparecimentos/hoje"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("Deve buscar custodiados inadimplentes")
    void testFindInadimplentes() throws Exception {
        custodiado.setStatus(StatusComparecimento.INADIMPLENTE);
        List<PessoaMonitorada> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.findInadimplentes()).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas/inadimplentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status", is("INADIMPLENTE")));
    }

    // ========== TESTES DE BUSCA ==========

    @Test
    @DisplayName("Deve buscar custodiados por termo")
    void testBuscar() throws Exception {
        List<PessoaMonitorada> custodiados = Arrays.asList(custodiado);
        when(custodiadoService.buscarPorNomeOuProcesso("João")).thenReturn(custodiados);
        when(custodiadoService.getEnderecoResumido(1L)).thenReturn("Centro, Salvador - BA");
        when(custodiadoService.getEnderecoCompleto(1L)).thenReturn("Av. Sete de Setembro, 1234, Centro, Salvador - BA");
        when(custodiadoService.getCidadeEstado(1L)).thenReturn("Salvador - BA");

        mockMvc.perform(get("/api/pessoas-monitoradas/buscar")
                        .param("termo", "João"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].nome", containsString("João")));
    }

    @Test
    @DisplayName("Deve retornar erro ao buscar com termo inválido")
    void testBuscarTermoInvalido() throws Exception {
        when(custodiadoService.buscarPorNomeOuProcesso("a"))
                .thenThrow(new IllegalArgumentException("Termo de busca deve ter pelo menos 2 caracteres"));

        mockMvc.perform(get("/api/pessoas-monitoradas/buscar")
                        .param("termo", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("2 caracteres")));
    }

    // ========== TESTES DE CONTAGEM ==========

    // ========== TESTE DE OPTIONS (CORS) ==========

    @Test
    @DisplayName("Deve responder ao OPTIONS request")
    void testOptions() throws Exception {
        mockMvc.perform(options("/api/pessoas-monitoradas"))
                .andExpect(status().isOk());
    }
}