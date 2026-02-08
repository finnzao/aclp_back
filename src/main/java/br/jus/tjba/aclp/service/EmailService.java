package br.jus.tjba.aclp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String smtpHost;

    @Value("${spring.mail.port:587}")
    private String smtpPort;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${aclp.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${aclp.email.remetente:noreply@tjba.jus.br}")
    private String emailRemetente;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("EmailService inicializado - Sender: {}", mailSender != null ? "Spring Mail" : "Manual");
    }

    public void enviarEmail(String destinatario, String assunto, String conteudo) {
        if (!emailEnabled) {
            log.warn("Envio de email desabilitado - destinatário: {}", destinatario);
            simularEnvioEmail(destinatario, assunto, conteudo);
            return;
        }

        try {
            if (mailSender != null) {
                enviarComSpringMail(destinatario, assunto, conteudo);
            } else {
                enviarComJavaMailManual(destinatario, assunto, conteudo);
            }

            log.info("Email enviado com sucesso - Destinatário: {}", destinatario);

        } catch (Exception e) {
            log.error("Erro ao enviar email para: " + destinatario, e);

            if (isDevelopmentMode()) {
                log.warn("Modo desenvolvimento - simulando envio de email");
                simularEnvioEmail(destinatario, assunto, conteudo);
            } else {
                throw new RuntimeException("Falha ao enviar email: " + e.getMessage());
            }
        }
    }

    private void enviarComSpringMail(String destinatario, String assunto, String conteudo) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(emailRemetente);
        helper.setTo(destinatario);
        helper.setSubject(assunto);
        helper.setText(conteudo, false);

        mailSender.send(message);
        log.debug("Email enviado via Spring Mail");
    }

    /**
     * Implementação manual usando JavaMail diretamente.
     *
     * FIX #4: Removido configurarSSLInseguro() que chamava
     * HttpsURLConnection.setDefaultSSLSocketFactory() com TrustManager que
     * aceitava qualquer certificado. Isso afetava TODA a JVM globalmente
     * (não só o email), desabilitando verificação SSL para HttpClient,
     * RestTemplate, WebClient, etc.
     *
     * Agora usa mail.smtp.ssl.trust com o host específico do SMTP,
     * que é o mecanismo correto do JavaMail para confiar no servidor.
     */
    private void enviarComJavaMailManual(String destinatario, String assunto, String conteudo) throws Exception {
        log.info("Enviando email manual - Host: {}, Port: {}, User: {}", smtpHost, smtpPort, smtpUsername);

        if (smtpUsername.isEmpty() || smtpPassword.isEmpty()) {
            throw new IllegalStateException("Configurações SMTP não definidas (username/password)");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2,TLSv1.3");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailRemetente));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        message.setSubject(assunto);
        message.setText(conteudo);
        message.setSentDate(new java.util.Date());

        Transport.send(message);
        log.debug("Email enviado via JavaMail manual");
    }

    private void simularEnvioEmail(String destinatario, String assunto, String conteudo) {
        log.info("=".repeat(80));
        log.info("SIMULAÇÃO DE EMAIL");
        log.info("=".repeat(80));
        log.info("Para: {}", destinatario);
        log.info("Assunto: {}", assunto);
        log.info("Data/Hora: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        log.info("-".repeat(80));
        log.info("Conteúdo:");
        log.info(conteudo);
        log.info("=".repeat(80));

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isDevelopmentMode() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return profile.contains("dev") || profile.contains("test");
    }

    public void enviarEmailVerificacao(String destinatario, String codigo, String tipoUsuario, int validadeMinutos) {
        String assunto = "Código de Verificação - Sistema ACLP TJBA";
        String conteudo = criarConteudoVerificacao(codigo, tipoUsuario, validadeMinutos);
        enviarEmail(destinatario, assunto, conteudo);
    }

    private String criarConteudoVerificacao(String codigo, String tipoUsuario, int validadeMinutos) {
        String tipoTexto = "ADMIN".equals(tipoUsuario) ? "Administrador" : "Usuário";
        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        return String.format("""
                TRIBUNAL DE JUSTIÇA DA BAHIA
                Sistema ACLP - Acompanhamento de Comparecimento em Liberdade Provisória
                
                CÓDIGO DE VERIFICAÇÃO
                
                Prezado(a) usuário(a),
                
                Você solicitou um código de verificação para acessar o Sistema ACLP.
                
                SEU CÓDIGO DE VERIFICAÇÃO: %s
                
                Detalhes da solicitação:
                Válido por: %d minutos
                Tipo de acesso: %s
                Solicitado em: %s
                
                INSTRUÇÕES IMPORTANTES:
                - Digite este código na tela de verificação
                - Não compartilhe este código com outras pessoas
                - O código expira automaticamente em %d minutos
                - Se não foi você quem solicitou, ignore este email
                
                Precisa de ajuda?
                Entre em contato com o suporte técnico do TJBA
                
                Atenciosamente,
                Equipe de Tecnologia da Informação
                Tribunal de Justiça do Estado da Bahia
                
                Esta é uma mensagem automática do Sistema ACLP.
                Não responda este email.
                """, codigo, validadeMinutos, tipoTexto, dataHora, validadeMinutos);
    }

    public boolean testarConectividade() {
        try {
            enviarEmail(emailRemetente, "Teste de Conectividade - ACLP",
                    "Este é um email de teste do Sistema ACLP.\n\n" +
                            "Se você recebeu esta mensagem, o envio de emails está funcionando.\n\n" +
                            "Data/Hora: " + LocalDateTime.now());
            return true;
        } catch (Exception e) {
            log.error("Teste de conectividade falhou", e);
            return false;
        }
    }

    public String getEmailInfo() {
        return String.format("""
                Email Service Configuration:
                - Enabled: %s
                - Method: %s
                - SMTP Host: %s
                - SMTP Port: %s
                - Username: %s
                - Sender: %s
                - Development Mode: %s
                """,
                emailEnabled,
                mailSender != null ? "Spring Mail" : "Manual JavaMail",
                smtpHost,
                smtpPort,
                smtpUsername.isEmpty() ? "Not configured" : smtpUsername,
                emailRemetente,
                isDevelopmentMode());
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }
}