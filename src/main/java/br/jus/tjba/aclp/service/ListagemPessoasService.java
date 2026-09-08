package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.dto.PessoaMonitoradaListDTO;
import br.jus.tjba.aclp.model.enums.SituacaoPessoaMonitorada;
import br.jus.tjba.aclp.model.enums.StatusComparecimento;
import br.jus.tjba.aclp.repository.PessoaMonitoradaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Listagem paginada e exportacao de pessoas (SQL nativa unica por pagina). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListagemPessoasService {

    private final PessoaMonitoradaRepository custodiadoRepository;
    private final jakarta.persistence.EntityManager entityManager;

    // =====================================================================
    // LISTAGEM: uma SQL por pagina (LEFT JOIN do processo mais urgente).
    // Substitui o par Specification+entidade e o N+1 da natureza.
    // ponytail: subquery com ROW_NUMBER e portavel (H2 dos testes + PG);
    // trocar por LEFT JOIN LATERAL se processos passar de dezenas de milhares.
    // =====================================================================

    private static final String LISTA_SELECT = """
            SELECT c.id, c.public_id, c.nome, c.cpf, c.rg, c.situacao,
                   pp.numero_processo, pp.vara, pp.comarca, pp.periodicidade,
                   pp.ultimo_comparecimento, pp.proximo_comparecimento,
                   CASE WHEN EXISTS (SELECT 1 FROM processos e WHERE e.custodiado_id = c.id
                                     AND e.situacao_processo = 'ATIVO'
                                     AND e.natureza_vinculo = 'EXECUCAO_REGIME_ABERTO')
                        THEN CASE WHEN EXISTS (SELECT 1 FROM processos e2 WHERE e2.custodiado_id = c.id
                                               AND e2.situacao_processo = 'ATIVO'
                                               AND e2.natureza_vinculo = 'CAUTELAR')
                                  THEN 'MISTO' ELSE 'EXECUCAO' END
                        ELSE 'CAUTELAR' END AS natureza
            """;

    private static final String LISTA_FROM = """
             FROM pessoas_monitoradas c
             LEFT JOIN (
                 SELECT pr.*, ROW_NUMBER() OVER (
                     PARTITION BY pr.custodiado_id
                     ORDER BY pr.proximo_comparecimento ASC NULLS LAST) rn
                 FROM processos pr
                 WHERE pr.situacao_processo = 'ATIVO'
             ) pp ON pp.custodiado_id = c.id AND pp.rn = 1
            """;

    private String montarWhereLista(String nome, String cpf, String status, String natureza,
                                    java.util.Map<String, Object> params) {
        StringBuilder w = new StringBuilder(" WHERE c.situacao = 'ATIVO'");
        if (nome != null && !nome.isBlank()) {
            w.append(" AND LOWER(c.nome) LIKE :nome");
            params.put("nome", "%" + nome.toLowerCase().trim() + "%");
        }
        if (cpf != null && !cpf.isBlank()) {
            w.append(" AND (c.cpf LIKE :cpfLimpo OR c.cpf LIKE :cpf)");
            params.put("cpfLimpo", "%" + cpf.replaceAll("\\D", "") + "%");
            params.put("cpf", "%" + cpf.trim() + "%");
        }
        if ("INADIMPLENTE".equalsIgnoreCase(status)) {
            w.append(" AND pp.proximo_comparecimento < CURRENT_DATE");
        } else if ("EM_CONFORMIDADE".equalsIgnoreCase(status)) {
            w.append(" AND (pp.proximo_comparecimento IS NULL OR pp.proximo_comparecimento >= CURRENT_DATE)");
        }
        String existsExec = "EXISTS (SELECT 1 FROM processos e WHERE e.custodiado_id = c.id "
                + "AND e.situacao_processo = 'ATIVO' AND e.natureza_vinculo = 'EXECUCAO_REGIME_ABERTO')";
        if ("EXECUCAO".equalsIgnoreCase(natureza)) w.append(" AND ").append(existsExec);
        else if ("CAUTELAR".equalsIgnoreCase(natureza)) w.append(" AND NOT ").append(existsExec);
        return w.toString();
    }

    private String ordenacaoLista(String campo, String direcao) {
        String col = switch (campo == null ? "nome" : campo.toLowerCase().trim()) {
            case "cpf" -> "c.cpf";
            case "status", "proximocomparecimento", "proximo_comparecimento" -> "pp.proximo_comparecimento";
            case "ultimocomparecimento", "ultimo_comparecimento" -> "pp.ultimo_comparecimento";
            case "comarca" -> "pp.comarca";
            case "vara" -> "pp.vara";
            case "periodicidade" -> "pp.periodicidade";
            case "criadoem", "criado_em" -> "c.criado_em";
            default -> "c.nome";
        };
        String dir = "desc".equalsIgnoreCase(direcao) ? "DESC" : "ASC";
        return " ORDER BY " + col + " " + dir + " NULLS LAST, c.nome ASC";
    }

    @SuppressWarnings("unchecked")
    private List<PessoaMonitoradaListDTO> consultarLista(String nome, String cpf, String status,
                                                         String natureza, String ordenarPor,
                                                         String direcao, Integer page, Integer size) {
        var params = new java.util.HashMap<String, Object>();
        String sql = LISTA_SELECT + LISTA_FROM + montarWhereLista(nome, cpf, status, natureza, params)
                + ordenacaoLista(ordenarPor, direcao);
        var q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        if (page != null && size != null) {
            q.setFirstResult(page * size);
            q.setMaxResults(size);
        }
        List<Object[]> linhas = q.getResultList();
        return linhas.stream().map(this::mapearLinhaLista).toList();
    }

    private long contarLista(String nome, String cpf, String status, String natureza) {
        var params = new java.util.HashMap<String, Object>();
        String sql = "SELECT COUNT(*)" + LISTA_FROM + montarWhereLista(nome, cpf, status, natureza, params);
        var q = entityManager.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return ((Number) q.getSingleResult()).longValue();
    }

    private static LocalDate dataDe(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        return LocalDate.parse(v.toString());
    }

    private PessoaMonitoradaListDTO mapearLinhaLista(Object[] r) {
        LocalDate hoje = LocalDate.now();
        LocalDate proximo = dataDe(r[11]);
        Integer periodicidade = r[9] != null ? ((Number) r[9]).intValue() : null;
        long diasAtraso = proximo != null && proximo.isBefore(hoje)
                ? java.time.temporal.ChronoUnit.DAYS.between(proximo, hoje) : 0;
        String cpf = (String) r[3];
        String rg = (String) r[4];
        String documento = cpf != null && !cpf.isBlank() ? cpf
                : rg != null && !rg.isBlank() ? rg : "Sem documento";
        return PessoaMonitoradaListDTO.builder()
                .id(r[1] != null ? r[1].toString() : null)
                .numericId(((Number) r[0]).longValue())
                .nome((String) r[2])
                .cpf(cpf).rg(rg)
                .documentoExibicao(documento)
                .processo((String) r[6]).vara((String) r[7]).comarca((String) r[8])
                .status(diasAtraso > 0 ? StatusComparecimento.INADIMPLENTE : StatusComparecimento.EM_CONFORMIDADE)
                .situacao(SituacaoPessoaMonitorada.valueOf((String) r[5]))
                .ultimoComparecimento(dataDe(r[10]))
                .proximoComparecimento(proximo)
                .diasAtraso(diasAtraso)
                .periodicidade(periodicidade)
                .inadimplente(diasAtraso > 0)
                .comparecimentoHoje(hoje.equals(proximo))
                .urgente(periodicidade != null && diasAtraso >= periodicidade)
                .natureza((String) r[12])
                .build();
    }

    @Transactional(readOnly = true)
    public Page<PessoaMonitoradaListDTO> listarPaginado(
            int page, int size, String nome, String cpf,
            String status, String natureza, String ordenarPor, String direcao) {
        List<PessoaMonitoradaListDTO> conteudo =
                consultarLista(nome, cpf, status, natureza, ordenarPor, direcao, page, size);
        long total = contarLista(nome, cpf, status, natureza);
        return new org.springframework.data.domain.PageImpl<>(conteudo, PageRequest.of(page, size), total);
    }

    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> listarParaExportacao(
            String nome, String cpf, String status, String natureza,
            String ordenarPor, String direcao) {
        return consultarLista(nome, cpf, status, natureza, ordenarPor, direcao, null, null);
    }

    /** Mapeia dentro da transacao: o DTO le processos (LAZY) para derivar os dados. */
    @Transactional(readOnly = true)
    public List<PessoaMonitoradaListDTO> listarTodosAtivosDTO() {
        return custodiadoRepository.findAllActive().stream().map(PessoaMonitoradaListDTO::fromEntity).toList();
    }

}
