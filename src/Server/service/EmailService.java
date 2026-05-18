package Server.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

/**
 * EmailService — Manages email transmissions for authentication and payment verification.
 * 
 * Dynamically loads SMTP configurations from environment variables:
 *   - SMTP_HOST: The SMTP server host (defaults to "smtp.gmail.com")
 *   - SMTP_PORT: The SMTP server port (defaults to "587")
 *   - SMTP_USER: Username/email address for SMTP authentication
 *   - SMTP_PASS: Password or app-specific password for SMTP authentication
 *   - SMTP_FROM: Sender's email address (defaults to "chrionline.noreply@gmail.com")
 * 
 * Falls back to localhost SMTP and terminal simulation if credentials are not configured.
 */
public class EmailService {

    private static final Logger logger = LogManager.getLogger(EmailService.class);
    private static final EmailService INSTANCE = new EmailService();

    private EmailService() {}

    public static EmailService getInstance() {
        return INSTANCE;
    }

    /**
     * Sends a real email to the specified recipient.
     *
     * @param toEmail recipient's email address
     * @param subject subject of the email
     * @param body    body content of the email
     */
    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            logger.error("[EmailService] Recipient email is null or blank.");
            return;
        }

        final String host = System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com");
        final String port = System.getenv().getOrDefault("SMTP_PORT", "587");
        final String username = System.getenv("SMTP_USER");
        final String password = System.getenv("SMTP_PASS");
        final String fromEmail = System.getenv().getOrDefault("SMTP_FROM", "chrionline.noreply@gmail.com");

        // Log simulation backup for local developers
        System.out.println("==========================================");
        System.out.println("📬 CHRIONLINE EMAIL OUTBOX SIMULATION");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: " + subject);
        System.out.println("Body: " + body);
        System.out.println("==========================================");

        // Attempt actual SMTP transmission
        try {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", host);
            properties.put("mail.smtp.port", port);
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.ssl.protocols", "TLSv1.2");

            Session session;
            if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
                session = Session.getInstance(properties, new jakarta.mail.Authenticator() {
                    @Override
                    protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                        return new jakarta.mail.PasswordAuthentication(username, password);
                    }
                });
            } else {
                // If credentials are omitted, fallback silently to localhost (default developer SMTP server)
                logger.warn("[EmailService] SMTP credentials missing. Falling back to localhost:25");
                properties.clear();
                properties.put("mail.smtp.host", "localhost");
                properties.put("mail.smtp.port", "25");
                session = Session.getInstance(properties, null);
            }

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromEmail, "ChriOnline"));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            msg.setSubject(subject);
            msg.setText(body);

            // Using thread context to avoid blocking the main server threads on slow SMTP networks
            new Thread(() -> {
                try {
                    Transport.send(msg);
                    logger.info("[EmailService] Real email sent successfully to: " + toEmail);
                } catch (Exception e) {
                    logger.warn("[EmailService] Real email transmission failed: " + e.getMessage() 
                            + " (Please configure valid SMTP environment variables to receive real emails)");
                }
            }).start();

        } catch (Exception e) {
            logger.error("[EmailService] Initialisation / Queue failed: " + e.getMessage(), e);
        }
    }
}
