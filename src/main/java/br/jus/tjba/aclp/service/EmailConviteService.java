package br.jus.tjba.aclp.service;

import br.jus.tjba.aclp.model.Convite;
import br.jus.tjba.aclp.repository.ConviteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIX #11: Serviço separado para envio assíncrono de emails de convite.
 *
 * O @Async dentro da mesma classe (ConviteService) NÃO funciona porque
 * o proxy do Spring não intercepta chamadas internas (self-invocation).
 * Extraindo para uma classe separada, o proxy funciona corretamente e
 * o email é enviado de forma assíncrona em uma nova thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailConviteService {

    private final ConviteRepository conviteRepository;
    private final EmailService emailService;

    @Value("${aclp.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void enviarEmailConviteAsync(Long conviteId) {
        try {
            Convite convite = conviteRepository.findById(conviteId).orElse(null);

            if (convite == null) {
                log.error("Convite não encontrado para envio de email - ID: {}", conviteId);
                return;
            }

            String linkConvite = String.format("%s/invite/%s", frontendUrl, convite.getToken());

            String assunto = "Convite para Sistema ACLP - TJBA";

            String conteudo = String.format("""
                    Olá!
                    
                    Você foi convidado para acessar o Sistema ACLP do Tribunal de Justiça da Bahia.
                    
                    Perfil: %s
                    Comarca: %s
                    Departamento: %s
                    Email: %s
                    
                    Para criar sua conta, acesse o link abaixo:
                    %s
                    
                    IMPORTANTE: Este link é de uso único e válido até: %s
                    
                    Atenciosamente,
                    Sistema ACLP - TJBA
                    """,
                    convite.getTipoUsuario().getLabel(),
                    convite.getComarca() != null ? convite.getComarca() : "Não definida",
                    convite.getDepartamento() != null ? convite.getDepartamento() : "Não definido",
                    convite.getEmail(),
                    linkConvite,
                    convite.getExpiraEm()
            );

            emailService.enviarEmail(convite.getEmail(), assunto, conteudo);
            log.info("Email de convite enviado com sucesso para: {}", convite.getEmail());

        } catch (Exception e) {
            log.error("Erro ao enviar email de convite ID {}: {}", conviteId, e.getMessage(), e);
        }
    }
}