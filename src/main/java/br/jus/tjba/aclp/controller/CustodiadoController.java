package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import br.jus.tjba.aclp.dto.CustodiadoDTO;
import br.jus.tjba.aclp.dto.CustodiadoListDTO;
import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.service.CustodiadoService;
import br.jus.tjba.aclp.service.HistoricoEnderecoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/custodiados")
@RequiredArgsConstructor
@Tag(name = "Custodiados", description = "API para gerenciamento de custodiados em liberdade provisória")
@Slf4j
public class CustodiadoController {

    private final CustodiadoService custodiadoService;
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
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
    }

    @lombok.Data
    @lombok.Builder
    public static class CustodiadoResponseDTO {
        private String id;
        private String nome;
        private String cpf;
        private String rg;
        private String contato;
        private String processo;
        private String vara;
        private String comarca;
        private LocalDate dataDecisao;
        private Integer periodicidade;
        private String periodicidadeDescricao;
        private LocalDate dataComparecimentoInicial;
        private StatusComparecimento status;
        private LocalDate ultimoComparecimento;
        private LocalDate proximoComparecimento;
        private Long diasAtraso;
        private String observacoes;
        private EnderecoDetalhadoDTO endereco;
        private LocalDateTime criadoEm;
        private LocalDateTime atualizadoEm;
        private String identificacao;
        private boolean inadimplente;
        private boolean comparecimentoHoje;

        public static CustodiadoResponseDTO fromEntity(Custodiado custodiado,
                                                        HistoricoEnderecoService enderecoService) {
            EnderecoDetalhadoDTO enderecoDetalhado = null;
            try {
                Optional<br.jus.tjba.aclp.dto.HistoricoEnderecoDTO> enderecoOpt =
                        enderecoService.buscarEnderecoAtivo(custodiado.getId());
                if (enderecoOpt.isPresent()) {
                    br.jus.tjba.aclp.dto.HistoricoEnderecoDTO enderecoDTO = enderecoOpt.get();
                    enderecoDetalhado = EnderecoDetalhadoDTO.builder()
                            .id(enderecoDTO.getId())
                            .cep(enderecoDTO.getCep())
                            .logradouro(enderecoDTO.getLogradouro())
                            .numero(enderecoDTO.getNumero())
                            .complemento(enderecoDTO.getComplemento())
                            .bairro(enderecoDTO.getBairro())
                            .cidade(enderecoDTO.getCidade())
                            .estado(enderecoDTO.getEstado())
                            .nomeEstado(enderecoDTO.getNomeEstado())
                            .regiaoEstado(enderecoDTO.getRegiaoEstado())
                            .dataInicio(enderecoDTO.getDataInicio())
                            .dataFim(enderecoDTO.getDataFim())
                            .ativo(enderecoDTO.getEnderecoAtivo())
                            .motivoAlteracao(enderecoDTO.getMotivoAlteracao())
                            .validadoPor(enderecoDTO.getValidadoPor())
                            .enderecoCompleto(enderecoDTO.getEnderecoCompleto())
                            .enderecoResumido(enderecoDTO.getEnderecoResumido())
                            .diasResidencia(enderecoDTO.getDiasResidencia())
                            .periodoResidencia(enderecoDTO.getPeriodoResidencia())
                            .criadoEm(enderecoDTO.getCriadoEm())
                            .atualizadoEm(enderecoDTO.getAtualizadoEm())
                            .build();
                }
            } catch (Exception e) {
                log.warn("Erro ao buscar endereço do custodiado publicId {}: {}",
                        custodiado.getPublicId(), e.getMessage());
            }

            return CustodiadoResponseDTO.builder()
                    .id(custodiado.getPublicId() != null ? custodiado.getPublicId().toString() : null)
                    .nome(custodiado.getNome())
                    .cpf(custodiado.getCpf())
                    .rg(custodiado.getRg())
                    .contato(custodiado.getContato())
                    .processo(custodiado.getProcesso())
                    .vara(custodiado.getVara())
                    .comarca(custodiado.getComarca())
                    .dataDecisao(custodiado.getDataDecisao())
                    .periodicidade(custodiado.getPeriodicidade())
                    .periodicidadeDescricao(custodiado.getPeriodicidadeDescricao())
                    .dataComparecimentoInicial(custodiado.getDataComparecimentoInicial())
                    .status(custodiado.getStatus())
                    .ultimoComparecimento(custodiado.getUltimoComparecimento())
                    .proximoComparecimento(custodiado.getProximoComparecimento())
                    .diasAtraso(custodiado.getDiasAtraso())
                    .observacoes(custodiado.getObservacoes())
                    .endereco(enderecoDetalhado)
                    .criadoEm(custodiado.getCriadoEm())
                    .atualizadoEm(custodiado.getAtualizadoEm())
                    .identificacao(custodiado.getIdentificacao())
                    .inadimplente(custodiado.isInadimplente())
                    .comparecimentoHoje(custodiado.isComparecimentoHoje())
                    .build();
        }
    }

    private String extrairMensagemValidacao(Exception e) {
        String mensagem = e.getMessage();
        if (mensagem != null) {
            if (mensagem.contains("processo") || mensagem.contains("Processo"))
                return "Formato do número do processo é inválido. Use o formato correto (ex: 0000000-00.0000.0.00.0000)";
            if (mensagem.contains("cpf") || mensagem.contains("CPF"))
                return "CPF inválido. Verifique o formato (000.000.000-00) ou se o número é válido";
            if (mensagem.contains("rg") || mensagem.contains("RG"))
                return "RG inválido. Verifique o formato";
            if (mensagem.contains("periodicidade"))
                return "Periodicidade deve ser um número positivo (dias)";
            if (mensagem.contains("data"))
                return "Formato de data inválido. Use o formato YYYY-MM-DD";
            if (mensagem.contains("nome") && mensagem.contains("tamanho"))
                return "Nome deve ter entre 2 e 100 caracteres";
            if (mensagem.contains("contato"))
                return "Formato do contato inválido. Use formato de telefone válido";
        }
        return mensagem;
    }

    private String extrairMensagemIntegridade(DataIntegrityViolationException e) {
        String mensagem = e.getMessage();
        if (mensagem != null) {
            if (mensagem.contains("cpf") || mensagem.contains("uk_custodiado_cpf"))
                return "CPF já cadastrado no sistema. Verifique se o custodiado já existe";
            if (mensagem.contains("rg") || mensagem.contains("uk_custodiado_rg"))
                return "RG já cadastrado no sistema. Verifique se o custodiado já existe";
            if (mensagem.contains("processo") && mensagem.contains("cpf"))
                return "Já existe um custodiado com este CPF neste processo";
            if (mensagem.contains("not null") || mensagem.contains("cannot be null")) {
                if (mensagem.contains("nome")) return "Nome é obrigatório";
                if (mensagem.contains("cpf")) return "CPF é obrigatório";
                if (mensagem.contains("processo")) return "Número do processo é obrigatório";
                return "Campo obrigatório não foi preenchido";
            }
        }
        return "Violação de integridade dos dados. Verifique se não há duplicação de informações";
    }

    @GetMapping
    @Operation(summary = "Listar todos os custodiados",
            description = "Retorna uma lista resumida de todos os custodiados cadastrados")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findAll() {
        log.info("Listando todos os custodiados (resumido)");
        try {
            List<Custodiado> custodiados = custodiadoService.findAllActive();
            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados listados com sucesso", response));
        } catch (Exception e) {
            log.error("Erro ao listar custodiados", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao listar custodiados. Tente novamente"));
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar custodiados por status")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findByStatus(
            @Parameter(description = "Status do comparecimento") @PathVariable StatusComparecimento status) {
        log.info("Buscando custodiados por status: {}", status);
        try {
            List<Custodiado> custodiados = custodiadoService.findByStatus(status);
            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados encontrados com sucesso", response));
        } catch (IllegalArgumentException e) {
            String valoresValidos = String.join(", ",
                    StatusComparecimento.EM_CONFORMIDADE.getCode(),
                    StatusComparecimento.INADIMPLENTE.getCode());
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Status inválido. Use um dos valores: " + valoresValidos));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por status: {}", status, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar custodiados por status. Tente novamente"));
        }
    }

    @GetMapping("/comparecimentos/hoje")
    @Operation(summary = "Comparecimentos de hoje")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findComparecimentosHoje() {
        log.info("Buscando custodiados com comparecimento hoje");
        try {
            List<Custodiado> custodiados = custodiadoService.findComparecimentosHoje();
            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados com comparecimento hoje listados com sucesso", response));
        } catch (Exception e) {
            log.error("Erro ao buscar comparecimentos de hoje", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar comparecimentos de hoje. Tente novamente"));
        }
    }

    @GetMapping("/inadimplentes")
    @Operation(summary = "Custodiados inadimplentes")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findInadimplentes() {
        log.info("Buscando custodiados inadimplentes");
        try {
            List<Custodiado> custodiados = custodiadoService.findInadimplentes();
            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiados inadimplentes listados com sucesso", response));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados inadimplentes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar custodiados inadimplentes. Tente novamente"));
        }
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar custodiados por termo")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> buscar(
            @Parameter(description = "Termo de busca") @RequestParam String termo) {
        log.info("Buscando custodiados por termo: {}", termo);
        try {
            if (termo == null || termo.trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Termo de busca não pode ser vazio"));
            if (termo.trim().length() < 2)
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Termo de busca deve ter pelo menos 2 caracteres"));

            List<Custodiado> custodiados = custodiadoService.buscarPorNomeOuProcesso(termo);
            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Busca realizada com sucesso", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Termo de busca inválido: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por termo: {}", termo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao realizar busca. Tente novamente"));
        }
    }

    @GetMapping("/processo/{processo}")
    @Operation(summary = "Buscar custodiados por processo")
    public ResponseEntity<ApiResponse<List<CustodiadoListDTO>>> findByProcesso(
            @Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Buscando custodiados por processo: {}", processo);
        try {
            if (processo == null || processo.trim().isEmpty())
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Número do processo não pode ser vazio"));

            List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);
            if (custodiados.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Nenhum custodiado encontrado com processo: " + processo));

            List<CustodiadoListDTO> response = custodiados.stream()
                    .map(CustodiadoListDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.success("Custodiados encontrados com sucesso", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Número do processo com formato inválido: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiados por processo: {}", processo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar custodiados por processo. Tente novamente"));
        }
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Buscar custodiado por ID (UUID)")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> findById(
            @Parameter(description = "UUID público do custodiado") @PathVariable String publicId) {
        log.info("Buscando custodiado por publicId: {}", publicId);
        try {
            return custodiadoService.findByPublicId(publicId)
                    .map(custodiado -> ResponseEntity.ok(
                            ApiResponse.success("Custodiado encontrado com sucesso",
                                    CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService))))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Custodiado não encontrado com ID: " + publicId)));
        } catch (Exception e) {
            log.error("Erro ao buscar custodiado publicId: {}", publicId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao buscar custodiado. Tente novamente"));
        }
    }

    @PostMapping
    @Operation(summary = "Cadastrar novo custodiado")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> save(@Valid @RequestBody CustodiadoDTO dto) {
        log.info("Cadastrando novo custodiado - Processo: {}, Nome: {}", dto.getProcesso(), dto.getNome());
        try {
            if (dto.getNome() == null || dto.getNome().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Nome é obrigatório"));
            if (dto.getCpf() == null || dto.getCpf().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("CPF é obrigatório"));
            if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Número do processo é obrigatório"));
            if (dto.getId() != null) dto.setId(null);

            Custodiado custodiado = custodiadoService.save(dto);
            log.info("Custodiado cadastrado com sucesso. publicId: {}", custodiado.getPublicId());
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.success("Custodiado cadastrado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(extrairMensagemValidacao(e)));
        } catch (DateTimeParseException e) {
            log.warn("Erro de formato de data ao cadastrar custodiado: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Formato de data inválido. Use o formato YYYY-MM-DD"));
        } catch (Exception e) {
            log.error("Erro inesperado ao cadastrar custodiado", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro interno ao cadastrar custodiado. Verifique os dados e tente novamente"));
        }
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Atualizar custodiado por UUID")
    public ResponseEntity<ApiResponse<CustodiadoResponseDTO>> update(
            @PathVariable String publicId, @Valid @RequestBody CustodiadoDTO dto) {
        log.info("Atualizando custodiado publicId: {}", publicId);
        try {
            if (dto.getNome() == null || dto.getNome().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Nome é obrigatório"));
            if (dto.getCpf() == null || dto.getCpf().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("CPF é obrigatório"));
            if (dto.getProcesso() == null || dto.getProcesso().trim().isEmpty())
                return ResponseEntity.badRequest().body(ApiResponse.error("Número do processo é obrigatório"));

            Custodiado custodiado = custodiadoService.updateByPublicId(publicId, dto);
            log.info("Custodiado atualizado com sucesso. publicId: {}", custodiado.getPublicId());
            return ResponseEntity.ok(
                    ApiResponse.success("Custodiado atualizado com sucesso",
                            CustodiadoResponseDTO.fromEntity(custodiado, historicoEnderecoService)));
        } catch (EntityNotFoundException e) {
            log.warn("Custodiado não encontrado para atualização. publicId: {}", publicId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Custodiado não encontrado com ID: " + publicId));
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao atualizar custodiado publicId {}: {}", publicId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(extrairMensagemIntegridade(e)));
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao atualizar custodiado publicId {}: {}", publicId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(extrairMensagemValidacao(e)));
        } catch (DateTimeParseException e) {
            log.warn("Erro de formato de data ao atualizar custodiado publicId {}: {}", publicId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Formato de data inválido. Use o formato YYYY-MM-DD"));
        } catch (Exception e) {
            log.error("Erro inesperado ao atualizar custodiado publicId: {}", publicId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro interno ao atualizar custodiado. Verifique os dados e tente novamente"));
        }
    }

    @DeleteMapping("/{publicId}")
    @Operation(summary = "Excluir custodiado por UUID")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String publicId) {
        log.info("Excluindo custodiado publicId: {}", publicId);
        try {
            custodiadoService.deleteByPublicId(publicId);
            log.info("Custodiado excluído com sucesso. publicId: {}", publicId);
            return ResponseEntity.ok(ApiResponse.success("Custodiado excluído com sucesso"));
        } catch (EntityNotFoundException e) {
            log.warn("Custodiado não encontrado para exclusão. publicId: {}", publicId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Custodiado não encontrado com ID: " + publicId));
        } catch (DataIntegrityViolationException e) {
            log.warn("Erro de integridade ao excluir custodiado publicId {}: {}", publicId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Não é possível excluir este custodiado pois possui registros relacionados"));
        } catch (IllegalArgumentException e) {
            log.warn("Erro de validação ao excluir custodiado publicId {}: {}", publicId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao excluir custodiado publicId: {}", publicId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro interno ao excluir custodiado. Tente novamente"));
        }
    }

    @GetMapping("/processo/{processo}/count")
    @Operation(summary = "Contar custodiados por processo")
    public ResponseEntity<ApiResponse<Long>> countByProcesso(
            @Parameter(description = "Número do processo") @PathVariable String processo) {
        log.info("Contando custodiados no processo: {}", processo);
        try {
            if (processo == null || processo.trim().isEmpty())
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Número do processo não pode ser vazio"));

            List<Custodiado> custodiados = custodiadoService.findByProcesso(processo);
            long count = custodiados.size();
            return ResponseEntity.ok(
                    ApiResponse.success(String.format("Processo %s tem %d custodiado(s)", processo, count), count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Número do processo com formato inválido: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Erro ao contar custodiados por processo: {}", processo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Erro ao contar custodiados. Tente novamente"));
        }
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> options() {
        return ResponseEntity.ok().build();
    }
}
