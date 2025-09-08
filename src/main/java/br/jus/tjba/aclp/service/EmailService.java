package br.jus.tjba.aclp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// Use ONLY Jakarta Mail imports (jakarta.mail.*)
import jakarta.mail.*;
import jakarta.mail.internet.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Serviço de email com implementação manual completa
 * Funciona com ou sem Spring Mail, usando JavaMail diretamente
 */
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

    // Construtor que aceita JavaMailSender opcional
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("EmailService inicializado - Sender: {}", mailSender != null ? "Spring Mail" : "Manual");
    }

    /**
     * Método principal para envio de email
     * Tenta usar Spring Mail primeiro, depois implementação manual
     */
    public void enviarEmail(String destinatario, String assunto, String conteudo) {
        if (!emailEnabled) {
            log.warn("Envio de email desabilitado - destinatário: {}", destinatario);
            // Em desenvolvimento, apenas simula o envio
            simularEnvioEmail(destinatario, assunto, conteudo);
            return;
        }

        try {
            // Tentar usar Spring Mail se disponível
            if (mailSender != null) {
                enviarComSpringMail(destinatario, assunto, conteudo);
            } else {
                // Fallback para implementação manual
                enviarComJavaMailManual(destinatario, assunto, conteudo);
            }

            log.info("Email enviado com sucesso - Destinatário: {}", destinatario);

        } catch (Exception e) {
            log.error("Erro ao enviar email para: " + destinatario, e);

            // Em caso de erro, simular envio para desenvolvimento
            if (isDevelopmentMode()) {
                log.warn("Modo desenvolvimento - simulando envio de email");
                simularEnvioEmail(destinatario, assunto, conteudo);
            } else {
                throw new RuntimeException("Falha ao enviar email: " + e.getMessage());
            }
        }
    }

    /**
     * Envio usando Spring Mail (quando disponível)
     */
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
     * Implementação manual usando JavaMail diretamente
     * Funciona independente do Spring Mail
     */
    private void enviarComJavaMailManual(String destinatario, String assunto, String conteudo) throws Exception {
        log.info("Enviando email manual - Host: {}, Port: {}, User: {}", smtpHost, smtpPort, smtpUsername);

        // Verificar se tem configurações mínimas
        if (smtpUsername.isEmpty() || smtpPassword.isEmpty()) {
            throw new IllegalStateException("Configurações SMTP não definidas (username/password)");
        }

        // Configurar propriedades SMTP
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        // Para desenvolvimento, desabilitar verificação SSL
        if (isDevelopmentMode()) {
            props.put("mail.smtp.ssl.trust", "*");
            configurarSSLInseguro();
        }

        // Criar sessão
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        // Criar mensagem
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(emailRemetente));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
        message.setSubject(assunto);
        message.setText(conteudo);
        message.setSentDate(new java.util.Date());

        // Enviar
        Transport.send(message);
        log.debug("Email enviado via JavaMail manual");
    }

    /**
     * Simula envio de email para desenvolvimento
     */
    private void simularEnvioEmail(String destinatario, String assunto, String conteudo) {
        log.info("=".repeat(80));
        log.info("📧 SIMULAÇÃO DE EMAIL");
        log.info("=".repeat(80));
        log.info("Para: {}", destinatario);
        log.info("Assunto: {}", assunto);
        log.info("Data/Hora: {}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        log.info("-".repeat(80));
        log.info("Conteúdo:");
        log.info(conteudo);
        log.info("=".repeat(80));

        // Simular delay de envio
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Configura SSL inseguro para desenvolvimento
     */
    private void configurarSSLInseguro() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        } catch (Exception e) {
            log.warn("Erro ao configurar SSL inseguro: {}", e.getMessage());
        }
    }

    /**
     * Verifica se está em modo desenvolvimento
     */
    private boolean isDevelopmentMode() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return profile.contains("dev") || profile.contains("test");
    }

    /**
     * Envia email de verificação com template personalizado
     */
    public void enviarEmailVerificacao(String destinatario, String codigo, String tipoUsuario, int validadeMinutos) {
        String assunto = "Código de Verificação - Sistema ACLP TJBA";
        String conteudo = criarConteudoVerificacao(codigo, tipoUsuario, validadeMinutos);
        enviarEmail(destinatario, assunto, conteudo);
    }

    /**
     * Cria conteúdo do email de verificação
     */
    private String criarConteudoVerificacao(String codigo, String tipoUsuario, int validadeMinutos) {
        String tipoTexto = "ADMIN".equals(tipoUsuario) ? "Administrador" : "Usuário";
        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        return String.format("""
                🏛️ TRIBUNAL DE JUSTIÇA DA BAHIA
                Sistema ACLP - Acompanhamento de Comparecimento em Liberdade Provisória
                
                ═══════════════════════════════════════════════════════════════
                CÓDIGO DE VERIFICAÇÃO
                ═══════════════════════════════════════════════════════════════
                
                Olá,
                
                Você solicitou um código de verificação para cadastro no Sistema ACLP.
                
                📧 SEU CÓDIGO DE VERIFICAÇÃO: %s
                
                ⏰ Este código é válido por %d minutos
                👤 Tipo de usuário: %s
                🕐 Solicitado em: %s
                
                ⚠️  IMPORTANTE:
                • Use este código apenas se você solicitou o cadastro
                • Não compartilhe este código com terceiros
                • O código expira automaticamente após %d minutos
                • Caso não tenha solicitado, ignore este email
                
                ═══════════════════════════════════════════════════════════════
                
                Se precisar de ajuda, entre em contato com o suporte técnico.
                
                Atenciosamente,
                Equipe ACLP - TJBA
                
                ---
                Esta é uma mensagem automática, não responda este email.
                Sistema ACLP - Versão 2.0.0
                """, codigo, validadeMinutos, tipoTexto, dataHora, validadeMinutos);
    }

    /**
     * Testa configuração de email
     */
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

    /**
     * Informações sobre configuração
     */
    public String getEmailInfo() {
        return String.format("""
                📧 Email Service Configuration:
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

    /**
     * Verifica se serviço está funcional
     */
    public boolean isEmailEnabled() {
        return emailEnabled;
    }
}