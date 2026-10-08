package br.jus.tjba.aclp.scheduler;

import br.jus.tjba.aclp.dto.ProvidenciaDTO.ProvidenciaResponse;
import br.jus.tjba.aclp.model.Usuario;
import br.jus.tjba.aclp.repository.UsuarioRepository;
import br.jus.tjba.aclp.service.EmailService;
import br.jus.tjba.aclp.service.ExecucaoPenalService;
import br.jus.tjba.aclp.util.TempoAtual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProvidenciaScheduler {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExecucaoPenalService execucaoPenalService;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @Value("${aclp.frontend.url:http://localhost}")
    private String frontendUrl;

    @Scheduled(cron = "0 0 7 * * MON-FRI", zone = "America/Bahia")
    public void rotinaDiaria() {
        int criadas = execucaoPenalService.gerarProvidenciasExtincao();
        if (criadas > 0) log.info("{} providência(s) de atestado de pena abertas (D-60)", criadas);
        enviarResumoDiario();
    }

    public void enviarResumoDiario() {
        List<ProvidenciaResponse> pendentes = execucaoPenalService.listarPendentes();
        LocalDate hoje = TempoAtual.hoje();
        List<ProvidenciaResponse> vencidas = pendentes.stream().filter(ProvidenciaResponse::isVencida).toList();
        List<ProvidenciaResponse> semana = pendentes.stream()
                .filter(p -> !p.isVencida() && p.getPrazo() != null && !p.getPrazo().isAfter(hoje.plusDays(7)))
                .toList();
        if (vencidas.isEmpty() && semana.isEmpty()) return;

        String corpo = montarCorpo(vencidas, semana) + "\nPainel: " + frontendUrl + "/dashboard/execucoes\n";
        String assunto = "SCC: " + vencidas.size() + " providência(s) vencida(s), " + semana.size() + " vencem esta semana";
        for (Usuario u : usuarioRepository.findAllAtivos()) {
            if (u.getEmail() != null && !u.getEmail().isBlank()) emailService.enviarEmail(u.getEmail(), assunto, corpo);
        }
    }

    static String montarCorpo(List<ProvidenciaResponse> vencidas, List<ProvidenciaResponse> semana) {
        StringBuilder sb = new StringBuilder();
        if (!vencidas.isEmpty()) {
            sb.append("VENCIDAS\n");
            vencidas.forEach(p -> sb.append(linha(p)));
            sb.append('\n');
        }
        if (!semana.isEmpty()) {
            sb.append("VENCEM NOS PRÓXIMOS 7 DIAS\n");
            semana.forEach(p -> sb.append(linha(p)));
        }
        return sb.toString();
    }

    private static String linha(ProvidenciaResponse p) {
        return "- " + (p.getPrazo() != null ? BR.format(p.getPrazo()) : "sem prazo") + " | "
                + p.getCustodiadoNome() + " | PEC " + p.getNumeroExecucao() + " | " + p.getTipoDescricao()
                + (p.getDescricao() != null ? " — " + p.getDescricao() : "")
                + (p.isPrioritaria() ? " [PRIORITÁRIA]" : "") + "\n";
    }
}
