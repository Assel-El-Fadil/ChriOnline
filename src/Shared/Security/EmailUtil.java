package Shared.Security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Properties;

public class EmailUtil {
    private static final Logger logger = LogManager.getLogger(EmailUtil.class);

    private static String smtpEmail = "xassil7@gmail.com";
    private static String smtpPassword = "uukt jsiz zuhs wuan";
    private static boolean isConfigLoaded = false;

    public static void sendMail(String recipientEmail, String subject, String body)
            throws IOException, MessagingException {

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.put("mail.smtp.ssl.trust", "smtp.gmail.com");

        Session session = Session.getInstance(properties, new jakarta.mail.Authenticator() {
            @Override
            protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                return new jakarta.mail.PasswordAuthentication(smtpEmail, smtpPassword);
            }
        });

        Message msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(smtpEmail, "ChriOnline"));
        msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
        msg.setSubject(subject);
        msg.setText(body);
        
        Transport.send(msg);
        logger.info("[EmailUtil] Successfully sent email to " + recipientEmail);
    }
}
