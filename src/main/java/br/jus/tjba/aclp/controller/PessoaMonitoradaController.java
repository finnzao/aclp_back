package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.*;
import br.jus.tjba.aclp.model.PessoaMonitorada;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.CadastroPessoaService;
import br.jus.tjba.aclp.service.ListagemPessoasService;
import br.jus.tjba.aclp.service.PessoaMonitoradaService;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pessoas-monitoradas")
@RequiredArgsConstructor
@Tag(name = "Pessoas monitoradas", description = "API para gerenciamento de custodiados em liberdade provisória")
@Slf4j
public class PessoaMonitoradaController {

    private final PessoaMonitoradaService custodiadoService;
    private final ListagemPessoasService listagemService;
    private final CadastroPessoaService cadastroService;
    private final HistoricoEnderecoService historicoEnderecoService;

    @lombok.Data
    @lombok.Builder
    public static class EnderecoDetalhadoDTO {
        private Long id;
        private String cep;
        private String logradouro;
        private String numero;
        private String complemento;
        private String bairro;
        private String cidade;
        private String estado;
        private String nomeEstado;
        private String regiaoEstado;
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private Boolean ativo;
        private String motivoAlteracao;
        private String validadoPor;
        private String enderecoCompleto;
        private String enderecoResumido;
        private Long diasResidencia;
        private String periodoResidencia;
        private java.time.LocalDateTime criadoEm;
        private java.time.LocalDateTime atualizadoEm;
    }

    @lombok.Data
    @lombok.Builder
    public static class PessoaMonitoradaResponseDTO {
        private String id;
        private String nome;
        private String cpf;
        private String rg;
        private String contato;
        private boolean contatoPendente;
        private String processo;
        private String vara;
        private String comarca;
        private LocalDate dataDecisao;
        private Integer periodicidade;
        private String periodicidadeDescricao;
        private LocalDate dataComparecimentoInicial;
        private StatusComparecimento status;
        private br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada situacao;
        private br.jus.tjba.aclp.model.enums.MotivoArquivamento motivoArquivamento;
        private LocalDate ultimoComparecimento;
        private LocalDate proximoComparecimento;
        private Long diasAtraso;
        private String observacoes;
        private EnderecoDetalhadoDTO endereco;
        private java.time.LocalDateTime criadoEm;
        private java.time.LocalDateTime atualizadoEm;
        private String identificacao;
        private boolean inadimplente;
        private boolean comparecimentoHoje;

        public static PessoaMonitoradaResponseDTO fromEntity(PessoaMonitorada custodiado,
                                                        HistoricoEnderecoService enderecoService) {
            EnderecoDetalhadoDTO enderecoDetalhado = null;
            Optional<HistoricoEnderecoDTO> enderecoOpt =
                    enderecoService.buscarEnderecoAtivo(custodiado.getId());
            if (enderecoOpt.isPresent()) {
                HistoricoEnderecoDTO enderecoDTO = enderecoOpt.get();
                enderecoDetalhado = EnderecoDetalhadoDTO.builder()
                        .id(enderecoDTO.getId()).cep(enderecoDTO.getCep())
                        .logradouro(enderecoDTO.getLogradouro()).numero(enderecoDTO.getNumero())
                        .complemento(enderecoDTO.getComplemento()).bairro(enderecoDTO.getBairro())
                        .cidade(enderecoDTO.getCidade()).estado(enderecoDTO.getEstado())
                        .nomeEstado(enderecoDTO.getNomeEstado()).regiaoEstado(enderecoDTO.getRegiaoEstado())
                        .dataInicio(enderecoDTO.getDataInicio()).dataFim(enderecoDTO.getDataFim())
                        .ativo(enderecoDTO.getEnderecoAtivo()).motivoAlteracao(enderecoDTO.getMotivoAlteracao())
                        .validadoPor(enderecoDTO.getValidadoPor())
                        .enderecoCompleto(enderecoDTO.getEnderecoCompleto())
                        .enderecoResumido(enderecoDTO.getEnderecoResumido())
                        .diasResidencia(enderecoDTO.getDiasResidencia())
                        .periodoResidencia(enderecoDTO.getPeriodoResidencia())
                        .criadoEm(enderecoDTO.getCriadoEm()).atualizadoEm(enderecoDTO.getAtualizadoEm())
                        .build();
            }

            return PessoaMonitoradaResponseDTO.builder()
                    .id(custodiado.getPublicId() != null ? custodiado.getPublicId().toString() : null)
                    .nome(custodiado.getNome()).cpf(custodiado.getCpf()).rg(custodiado.getRg())
                    .contato(custodiado.getContato()).contatoPendente(custodiado.isContatoPendente())
                    .processo(custodiado.getProcesso()).vara(custodiado.getVara()).comarca(custodiado.getComarca())
                    .dataDecisao(custodiado.getDataDecisao()).periodicidade(custodiado.getPeriodicidade())
                    .periodicidadeDescricao(custodiado.getPeriodicidadeDescricao())
                    .dataComparecimentoInicial(custodiado.getDataComparecimentoInicial())
                    .status(custodiado.getStatusEfetivo())
                    .situacao(custodiado.getSituacao())
                    .motivoArquivamento(custodiado.getMotivoArquivamento())
                    .ultimoComparecimento(custodiado.getUltimoComparecimento())
                    .proximoComparecimento(custodiado.getProximoComparecimento())
                    .diasAtraso(custodiado.getDiasAtraso()).observacoes(custodiado.getObservacoes())
                    .endereco(enderecoDetalhado).criadoEm(custodiado.getCriadoEm())
                    .atualizadoEm(custodiado.getAtualizadoEm()).identificacao(custodiado.getIdentificacao())
                    .inadimplente(custodiado.isInadimplente()).comparecimentoHoje(custodiado.isComparecimentoHoje())
                    .build();
        }
    }

    // =====================================================================
    // CORREÇÃO DE PERFORMANCE: GET /custodiados com paginação retrocompatível
    // =====================================================================

    @GetMapping
    @Operation(summary = "Listar custodiados",
            description = "Sem parâmetros: retorna todos (retrocompatível). " +
                    "Com page/size: retorna página com filtros server-side.")
    public ResponseEntity<?> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String natureza,
            @RequestParam(required = false, defaultValue = "nome") String ordenarPor,
            @RequestParam(required = false, defaultValue = "asc") String direcao) {

        // Sem parâmetros → comportamento original (retrocompatibilidade)
        if (page == null && size == null && nome == null && cpf == null && status == null && natureza == null) {
            List<PessoaMonitoradaListDTO> response = listagemService.listarTodosAtivosDTO();
            return ResponseEntity.ok(ApiResponse.success("Pessoas monitoradas listadas com sucesso", response));
        }

        // CORREÇÃO DE PERFORMANCE: Paginação server-side
        int pageNum = (page != null) ? page : 0;
        int pageSize = (size != null) ? Math.min(size, 100) : 20;

        if (size != null && size > 100) {
            log.warn("[PERFORMANCE] Requisição com size={} reduzida para 100.", size);
        }

        Page<PessoaMonitoradaListDTO> resultado = listagemService
                .listarPaginado(pageNum, pageSize, nome, cpf, status, natureza, ordenarPor, direcao);

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("success", true);
        resposta.put("message", "Pessoas monitoradas listadas com sucesso");
        resposta.put("data", resultado.getContent());
        resposta.put("paginaAtual", resultado.getNumber());
        resposta.put("totalPaginas", resultado.getTotalPages());
        resposta.put("totalItens", resultado.getTotalElements());
        resposta.put("itensPorPagina", resultado.getSize());
        resposta.put("temProxima", resultado.hasNext());
        resposta.put("temAnterior", resultado.hasPrevious());

        return ResponseEntity.ok(resposta);
    }

    // CORREÇÃO DE PERFORMANCE: Endpoint dedicado para exportação
    @GetMapping("/exportar")
    @Operation(summary = "Exportar custodiados para planilha")
    public ResponseEntity<?> exportarPessoasMonitoradas(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String natureza,
            @RequestParam(required = false, defaultValue = "nome") String ordenarPor,
            @RequestParam(required = false, defaultValue = "asc") String direcao) {

        List<PessoaMonitoradaListDTO> dados = listagemService
                .listarParaExportacao(nome, cpf, status, natureza, ordenarPor, direcao);

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("success", true);
        resposta.put("message", "Dados exportados com sucesso");
        resposta.put("data", dados);
        resposta.put("totalItens", dados.size());
        resposta.put("exportadoEm", LocalDateTime.now().toString());

        return ResponseEntity.ok(resposta);
    }

    // =====================================================================
    // Endpoints existentes (todos mantidos inalterados)
    // =====================================================================

    @PostMapping("/cadastro-inicial")
    @Operation(summary = "Cadastro inicial completo")
    public ResponseEntity<ApiResponse<CadastroInicialResponseDTO>> cadastroInicial(
            @Valid @RequestBody CadastroInicialDTO dto) {
        try {
            CadastroInicialResponseDTO response = cadastroService.cadastroInicial(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Cadastro inicial realizado com sucesso", response));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado no cadastro inicial", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro interno ao realizar cadastro."));
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PessoaMonitoradaListDTO>>> findByStatus(@PathVariable StatusComparecimento status) {
        List<PessoaMonitoradaListDTO> response = custodiadoService.findByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Pessoas monitoradas encontradas", response));
    }

    @GetMapping("/comparecimentos/hoje")
    public ResponseEntity<ApiResponse<List<PessoaMonitoradaListDTO>>> findComparecimentosHoje() {
        List<PessoaMonitoradaListDTO> response = custodiadoService.findComparecimentosHoje();
        return ResponseEntity.ok(ApiResponse.success("Comparecimentos de hoje", response));
    }

    @GetMapping("/inadimplentes")
    public ResponseEntity<ApiResponse<List<PessoaMonitoradaListDTO>>> findInadimplentes() {
        List<PessoaMonitoradaListDTO> response = custodiadoService.findInadimplentes();
        return ResponseEntity.ok(ApiResponse.success("Inadimplentes listados", response));
    }

    @GetMapping("/buscar")
    public ResponseEntity<ApiResponse<List<PessoaMonitoradaListDTO>>> buscar(@RequestParam String termo) {
        if (termo == null || termo.trim().isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.error("Termo de busca não pode ser vazio"));
        List<PessoaMonitoradaListDTO> response = custodiadoService.buscarPorNomeOuProcesso(termo);
        return ResponseEntity.ok(ApiResponse.success("Busca realizada", response));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PessoaMonitoradaResponseDTO>> findById(@PathVariable String publicId) {
        return custodiadoService.findByPublicId(publicId)
                .map(c -> ResponseEntity.ok(ApiResponse.success("Pessoa monitorada encontrada",
                        PessoaMonitoradaResponseDTO.fromEntity(c, historicoEnderecoService))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Pessoa monitorada não encontrada: " + publicId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PessoaMonitoradaResponseDTO>> save(@Valid @RequestBody PessoaMonitoradaDTO dto) {
        try {
            if (dto.getId() != null) dto.setId(null);
            PessoaMonitorada custodiado = cadastroService.save(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Pessoa monitorada cadastrada", PessoaMonitoradaResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao cadastrar custodiado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Erro interno ao cadastrar"));
        }
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<ApiResponse<PessoaMonitoradaResponseDTO>> update(@PathVariable String publicId, @Valid @RequestBody PessoaMonitoradaDTO dto) {
        PessoaMonitorada custodiado = cadastroService.updateByPublicId(publicId, dto);
        return ResponseEntity.ok(ApiResponse.success("Pessoa monitorada atualizada",
                PessoaMonitoradaResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('ADMIN')") // destrutivo: só administrador
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String publicId,
            @RequestParam(required = false) br.jus.tjba.aclp.model.enums.MotivoArquivamento motivo) {
        custodiadoService.deleteByPublicId(publicId, motivo);
        return ResponseEntity.ok(ApiResponse.success("Pessoa monitorada arquivada com sucesso"));
    }

    @PostMapping("/{publicId}/foragido")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> declararForagido(
            @PathVariable String publicId,
            @RequestBody Map<String, String> body,
            org.springframework.security.core.Authentication auth) {
        custodiadoService.declararForagido(publicId, body.get("motivo"), auth.getName());
        return ResponseEntity.ok(ApiResponse.success("Pessoa monitorada declarada foragida"));
    }

    @PostMapping("/{publicId}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reativar(@PathVariable String publicId) {
        custodiadoService.reativarPorPublicId(publicId);
        return ResponseEntity.ok(ApiResponse.success("Pessoa monitorada reativada"));
    }

    @GetMapping("/cpf-arquivado")
    public ResponseEntity<ApiResponse<Boolean>> cpfArquivado(@RequestParam String cpf) {
        return ResponseEntity.ok(ApiResponse.success("Verificado",
                custodiadoService.existeArquivadoComCpf(cpf)));
    }

    private String extrairMensagemIntegridade(DataIntegrityViolationException e) {
        String msg = e.getMessage();
        if (msg != null) {
            if (msg.contains("cpf")) return "CPF já cadastrado no sistema";
            if (msg.contains("rg")) return "RG já cadastrado no sistema";
        }
        return "Violação de integridade dos dados";
    }
}
