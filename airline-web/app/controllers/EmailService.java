package services;

import com.typesafe.config.Config;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

@Singleton
public class EmailService {

    private final String username;
    private final String password;
    private final Session session;

    @Inject
    public EmailService(Config config) {
        // Fail fast during startup if these configs are missing
        this.username = config.getString("mail.smtp.user");
        this.password = config.getString("mail.smtp.password");

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    public void sendEmail(String to, String subject, String bodyText) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(this.username));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(bodyText);

        Transport.send(message);
    }
}