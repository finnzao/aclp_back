package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.model.Custodiado;
import br.jus.tjba.aclp.model.HistoricoEndereco;
import br.jus.tjba.aclp.model.Processo;
import br.jus.tjba.aclp.repository.CustodiadoRepository;
import br.jus.tjba.aclp.repository.HistoricoEnderecoRepository;
import br.jus.tjba.aclp.repository.ProcessoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller responsável pela exportação de dados em formato Excel (.xlsx).
 */
@RestController
@RequestMapping("/api/exportar")
@RequiredArgsConstructor
@Tag(name = "Exportação", description = "Endpoints para exportação de dados em Excel")
@Slf4j
public class ExportacaoController {

    private final CustodiadoRepository custodiadoRepository;
    private final ProcessoRepository processoRepository;
    private final HistoricoEnderecoRepository historicoEnderecoRepository;

    /**
     * Exporta todos os custodiados ativos com seus processos e endereços.
     *
     * Estrutura da planilha (13 colunas):
     * - Colunas 1-4: dados pessoais (nome, CPF, RG, contato)
     * - Colunas 5-7: endereço ativo (logradouro+número, bairro, cidade/UF)
     * - Colunas 8-13: dados do processo (número, vara, comarca, status, próximo comp., situação)
     *
     * Custodiado com 3 processos = 3 linhas (dados pessoais e endereço repetidos)
     * Custodiado sem processo = 1 linha (colunas de processo vazias)
     *
     * A resposta é um arquivo .xlsx — o navegador inicia o download automaticamente.
     */
    @GetMapping("/custodiados-processos")
    @Operation(summary = "Exportar custodiados com processos e endereços em Excel",
            description = "Gera planilha .xlsx com todos os custodiados, endereços ativos e processos vinculados.")
    public ResponseEntity<byte[]> exportarCustodiadosComProcessos(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String comarca) {

        log.info("Exportação Excel solicitada — filtros: nome={}, status={}, comarca={}",
                nome, status, comarca);

        try {
            // Buscar todos os custodiados ativos
            List<Custodiado> custodiados = custodiadoRepository.findAllActive();

            // Aplicar filtros se informados
            if (nome != null && !nome.isBlank()) {
                String filtro = nome.toLowerCase().trim();
                custodiados = custodiados.stream()
                        .filter(c -> c.getNome().toLowerCase().contains(filtro))
                        .collect(Collectors.toList());
            }
            if (status != null && !status.isBlank()) {
                custodiados = custodiados.stream()
                        .filter(c -> c.getStatus() != null &&
                                c.getStatus().name().equalsIgnoreCase(status.trim()))
                        .collect(Collectors.toList());
            }
            if (comarca != null && !comarca.isBlank()) {
                String filtroComarca = comarca.toLowerCase().trim();
                custodiados = custodiados.stream()
                        .filter(c -> c.getComarca() != null &&
                                c.getComarca().toLowerCase().contains(filtroComarca))
                        .collect(Collectors.toList());
            }

            // Coletar IDs para busca em lote (evita N+1)
            List<Long> ids = custodiados.stream()
                    .map(Custodiado::getId)
                    .collect(Collectors.toList());

            // Buscar processos de todos os custodiados em UMA query
            Map<Long, List<Processo>> processosPorCustodiado = new HashMap<>();
            if (!ids.isEmpty()) {
                List<Processo> todosProcessos = processoRepository.findByCustodiadoIdIn(ids);
                processosPorCustodiado = todosProcessos.stream()
                        .collect(Collectors.groupingBy(p -> p.getCustodiado().getId()));
            }

            // Buscar endereços ativos de todos os custodiados em UMA query
            Map<Long, HistoricoEndereco> enderecosPorCustodiado = new HashMap<>();
            if (!ids.isEmpty()) {
                List<HistoricoEndereco> enderecos = historicoEnderecoRepository.findAllEnderecosAtivos();
                enderecosPorCustodiado = enderecos.stream()
                        .filter(e -> ids.contains(e.getCustodiado().getId()))
                        .collect(Collectors.toMap(
                                e -> e.getCustodiado().getId(),
                                e -> e,
                                (e1, e2) -> e1 // Se houver duplicata, pegar o primeiro
                        ));
            }

            // Gerar Excel
            byte[] excelBytes = gerarExcel(custodiados, processosPorCustodiado, enderecosPorCustodiado);

            // Nome do arquivo com data
            String dataAtual = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String nomeArquivo = "custodiados-processos-" + dataAtual + ".xlsx";

            log.info("Exportação concluída — {} custodiados, arquivo: {}",
                    custodiados.size(), nomeArquivo);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nomeArquivo + "\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);

        } catch (Exception e) {
            log.error("Erro ao gerar exportação Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gera o arquivo Excel com formatação profissional.
     */
    private byte[] gerarExcel(List<Custodiado> custodiados,
                               Map<Long, List<Processo>> processosPorCustodiado,
                               Map<Long, HistoricoEndereco> enderecosPorCustodiado) throws Exception {

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Custodiados e Processos");

            // ========== ESTILOS ==========

            // Cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Arial");
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            // Dados (texto)
            CellStyle dataStyle = criarEstiloDado(workbook);

            // Dados (centralizado)
            CellStyle centerStyle = criarEstiloDado(workbook);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Status inadimplente (fundo vermelho)
            CellStyle inadimplenteStyle = criarEstiloDado(workbook);
            inadimplenteStyle.setAlignment(HorizontalAlignment.CENTER);
            inadimplenteStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
            inadimplenteStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font inadimplenteFont = workbook.createFont();
            inadimplenteFont.setFontName("Arial");
            inadimplenteFont.setFontHeightInPoints((short) 10);
            inadimplenteFont.setColor(IndexedColors.DARK_RED.getIndex());
            inadimplenteFont.setBold(true);
            inadimplenteStyle.setFont(inadimplenteFont);

            // Status em conformidade (fundo verde)
            CellStyle conformidadeStyle = criarEstiloDado(workbook);
            conformidadeStyle.setAlignment(HorizontalAlignment.CENTER);
            conformidadeStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            conformidadeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Título
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setFontName("Arial");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            // Subtítulo
            CellStyle subtitleStyle = workbook.createCellStyle();
            Font subtitleFont = workbook.createFont();
            subtitleFont.setFontName("Arial");
            subtitleFont.setFontHeightInPoints((short) 9);
            subtitleFont.setItalic(true);
            subtitleFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitleStyle.setFont(subtitleFont);

            // ========== TÍTULO ==========
            int totalColunas = 13; // A até M

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("TJBA — Relatório de Custodiados e Processos");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, totalColunas - 1));

            Row subtitleRow = sheet.createRow(1);
            Cell subtitleCell = subtitleRow.createCell(0);
            subtitleCell.setCellValue("Gerado em: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")));
            subtitleCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, totalColunas - 1));

            // ========== CABEÇALHOS ==========
            String[] headers = {
                    "Nome", "CPF", "RG", "Contato",
                    "Endereço", "Bairro", "Cidade/UF",
                    "Número do Processo", "Vara", "Comarca",
                    "Status", "Próximo Comparecimento", "Situação do Processo"
            };

            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(30);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // ========== DADOS ==========
            int rowNum = 4;
            int totalLinhas = 0;

            for (Custodiado custodiado : custodiados) {
                List<Processo> processos = processosPorCustodiado
                        .getOrDefault(custodiado.getId(), Collections.emptyList());
                HistoricoEndereco endereco = enderecosPorCustodiado.get(custodiado.getId());

                if (processos.isEmpty()) {
                    // Custodiado sem processo: 1 linha
                    Row row = sheet.createRow(rowNum++);
                    preencherDadosCustodiado(row, custodiado, endereco,
                            dataStyle, centerStyle);
                    // Colunas de processo vazias
                    for (int col = 7; col < totalColunas; col++) {
                        Cell cell = row.createCell(col);
                        cell.setCellStyle(dataStyle);
                        if (col == 7) cell.setCellValue("—");
                    }
                    totalLinhas++;
                } else {
                    // Custodiado com processo(s): 1 linha por processo
                    for (Processo processo : processos) {
                        Row row = sheet.createRow(rowNum++);
                        preencherDadosCustodiado(row, custodiado, endereco,
                                dataStyle, centerStyle);
                        preencherDadosProcesso(row, processo, dataStyle, centerStyle,
                                inadimplenteStyle, conformidadeStyle);
                        totalLinhas++;
                    }
                }
            }

            // ========== RODAPÉ ==========
            rowNum += 1;
            Row totalRow = sheet.createRow(rowNum);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("Total de custodiados: " + custodiados.size() +
                    " | Total de linhas: " + totalLinhas);
            totalLabel.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, totalColunas - 1));

            // ========== LARGURA DAS COLUNAS ==========
            int[] larguras = {35, 18, 18, 18, 40, 22, 22, 30, 25, 22, 18, 22, 22};
            for (int i = 0; i < larguras.length; i++) {
                sheet.setColumnWidth(i, larguras[i] * 256);
            }

            // Cabeçalho fixo ao rolar
            sheet.createFreezePane(0, 4);

            // Filtro automático
            sheet.setAutoFilter(new CellRangeAddress(3, 3 + totalLinhas, 0, totalColunas - 1));

            // ========== GERAR BYTES ==========
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Cria estilo base para células de dados.
     */
    private CellStyle criarEstiloDado(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * Preenche colunas 0-6: dados pessoais + endereço do custodiado.
     */
    private void preencherDadosCustodiado(Row row, Custodiado custodiado,
                                           HistoricoEndereco endereco,
                                           CellStyle dataStyle, CellStyle centerStyle) {
        // Col 0: Nome
        Cell nome = row.createCell(0);
        nome.setCellValue(custodiado.getNome());
        nome.setCellStyle(dataStyle);

        // Col 1: CPF
        Cell cpf = row.createCell(1);
        cpf.setCellValue(custodiado.getCpf() != null ? custodiado.getCpf() : "—");
        cpf.setCellStyle(centerStyle);

        // Col 2: RG
        Cell rg = row.createCell(2);
        rg.setCellValue(custodiado.getRg() != null ? custodiado.getRg() : "—");
        rg.setCellStyle(centerStyle);

        // Col 3: Contato
        Cell contato = row.createCell(3);
        contato.setCellValue(custodiado.getContato() != null ? custodiado.getContato() : "—");
        contato.setCellStyle(centerStyle);

        // Col 4: Endereço (logradouro + número + complemento)
        Cell enderecoCell = row.createCell(4);
        if (endereco != null) {
            StringBuilder end = new StringBuilder();
            if (endereco.getLogradouro() != null) end.append(endereco.getLogradouro());
            if (endereco.getNumero() != null) end.append(", ").append(endereco.getNumero());
            if (endereco.getComplemento() != null && !endereco.getComplemento().isBlank()) {
                end.append(", ").append(endereco.getComplemento());
            }
            enderecoCell.setCellValue(end.toString());
        } else {
            enderecoCell.setCellValue("Não informado");
        }
        enderecoCell.setCellStyle(dataStyle);

        // Col 5: Bairro
        Cell bairro = row.createCell(5);
        bairro.setCellValue(endereco != null && endereco.getBairro() != null
                ? endereco.getBairro() : "—");
        bairro.setCellStyle(dataStyle);

        // Col 6: Cidade/UF
        Cell cidadeUf = row.createCell(6);
        if (endereco != null && endereco.getCidade() != null) {
            String uf = endereco.getEstado() != null ? "/" + endereco.getEstado() : "";
            cidadeUf.setCellValue(endereco.getCidade() + uf);
        } else {
            cidadeUf.setCellValue("—");
        }
        cidadeUf.setCellStyle(dataStyle);
    }

    /**
     * Preenche colunas 7-12: dados do processo.
     */
    private void preencherDadosProcesso(Row row, Processo processo,
                                         CellStyle dataStyle, CellStyle centerStyle,
                                         CellStyle inadimplenteStyle,
                                         CellStyle conformidadeStyle) {
        // Col 7: Número do processo
        Cell numProc = row.createCell(7);
        numProc.setCellValue(processo.getNumeroProcesso());
        numProc.setCellStyle(dataStyle);

        // Col 8: Vara
        Cell vara = row.createCell(8);
        vara.setCellValue(processo.getVara() != null ? processo.getVara() : "—");
        vara.setCellStyle(dataStyle);

        // Col 9: Comarca
        Cell comarca = row.createCell(9);
        comarca.setCellValue(processo.getComarca() != null ? processo.getComarca() : "—");
        comarca.setCellStyle(dataStyle);

        // Col 10: Status (com cor condicional)
        Cell statusCell = row.createCell(10);
        if (processo.getStatus() != null) {
            String statusText = processo.getStatus().name().replace("_", " ");
            statusCell.setCellValue(statusText);
            if ("INADIMPLENTE".equals(processo.getStatus().name())) {
                statusCell.setCellStyle(inadimplenteStyle);
            } else {
                statusCell.setCellStyle(conformidadeStyle);
            }
        } else {
            statusCell.setCellValue("—");
            statusCell.setCellStyle(centerStyle);
        }

        // Col 11: Próximo comparecimento
        Cell proxComp = row.createCell(11);
        if (processo.getProximoComparecimento() != null) {
            proxComp.setCellValue(processo.getProximoComparecimento()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        } else {
            proxComp.setCellValue("—");
        }
        proxComp.setCellStyle(centerStyle);

        // Col 12: Situação do processo
        Cell situacao = row.createCell(12);
        if (processo.getSituacaoProcesso() != null) {
            situacao.setCellValue(processo.getSituacaoProcesso().name());
        } else {
            situacao.setCellValue("—");
        }
        situacao.setCellStyle(centerStyle);
    }
}
