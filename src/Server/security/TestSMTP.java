package Server.security;

import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * Standalone SMTP Diagnostics Tool.
 * Run this to check the exact SMTP connection details and catch any errors.
 */
public class TestSMTP {
    public static void main(String[] args) {
        System.out.println("=== Starting SMTP Connection Diagnostics ===");

        final String host = System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com");
        final String port = System.getenv().getOrDefault("SMTP_PORT", "587");
        final String username = System.getenv("SMTP_USER");
        final String password = System.getenv("SMTP_PASS");
        final String fromEmail = System.getenv().getOrDefault("SMTP_FROM", "chrionline.noreply@gmail.com");

        System.out.println("SMTP Host: " + host);
        System.out.println("SMTP Port: " + port);
        System.out.println("SMTP User: " + (username == null ? "NOT CONFIGURED (null)" : username));
        System.out.println("SMTP Pass: " + (password == null ? "NOT CONFIGURED (null)" : "[PROTECTED]"));
        System.out.println("SMTP From: " + fromEmail);

        if (username == null || password == null) {
            System.err.println("\n[ERROR] SMTP_USER or SMTP_PASS environment variables are missing!");
            System.err.println("Please set them in your terminal before running this script.");
            return;
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.put("mail.debug", "true"); // Enables complete protocol logging!

        System.out.println("\nConnecting to SMTP server...");
        try {
            Session session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(fromEmail, "ChriOnline Diagnostic"));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(username));
            msg.setSubject("ChriOnline Real SMTP Test");
            msg.setText("Congratulations! Your real SMTP email system is fully functional!");

            System.out.println("Sending test email to " + username + "...");
            Transport.send(msg);
            System.out.println("\n[SUCCESS] Test email sent successfully! Please check your inbox.");

        } catch (AuthenticationFailedException e) {
            System.err.println("\n[FAILURE] SMTP Authentication Failed!");
            System.err.println("Possible causes: ");
            System.err.println("1. You used your main Gmail password instead of a 16-digit App Password.");
            System.err.println("2. The username or password spelling is incorrect.");
        } catch (MessagingException e) {
            System.err.println("\n[FAILURE] SMTP Messaging/Network Error!");
            System.err.println("Details: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("\n[FAILURE] Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
