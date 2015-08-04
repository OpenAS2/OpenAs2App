package org.openas2.logging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.params.CompositeParameters;
import org.openas2.params.ExceptionParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageMDNParameters;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;


public class EmailLogger extends BaseLogger {
    public static final String PARAM_FROM = "from";
    public static final String PARAM_TO = "to";
    public static final String PARAM_SMTPSERVER = "smtpserver";
    public static final String PARAM_SUBJECT = "subject";
    public static final String PARAM_BODY = "body";
    public static final String PARAM_BODYTEMPLATE = "bodytemplate";

    public void doLog(Level level, String msgText, Message message) {
    	
    	if (level != Level.ERROR)
    	{
    		return;
    	}
        try {
            String subject = getParameter(PARAM_SUBJECT, false);

            if (subject == null) {
                subject = getSubject(level, msgText);
            }

            sendMessage(subject, getFormatter().format(level, msgText));
        } catch (InvalidParameterException ipe) {
            ipe.printStackTrace();
        }
    }

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        getParameter(PARAM_FROM, true);
        getParameter(PARAM_TO, true);
        getParameter(PARAM_SMTPSERVER, true);
    }

    protected String getShowDefaults() {
        return VALUE_SHOW_TERMINATED;
    }

    protected String getSubject(Level level, String msg) {
        StringBuffer subject = new StringBuffer("OpenAS2 Log (" + level.getName() + "): " + msg);

        return subject.toString();
    }

    protected String getSubject(OpenAS2Exception e) {
        StringBuffer subject = new StringBuffer("OpenAS2 Exception: ");

        if (e instanceof WrappedException) {
            subject.append(((WrappedException) e).getSource().getClass().getName());
        } else {
            subject.append(e.getClass().getName());
        }

        subject.append(": ").append(e.getMessage());

        return subject.toString();
    }

    protected String getTemplateText() throws InvalidParameterException {
        try {
            File file = new File(getParameter(PARAM_BODYTEMPLATE, true));

            if (file.exists() && file.isFile()) {
                FileInputStream fIn = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fIn.read(data);
                fIn.close();

                return new String(data);
            }

            return "";
        } catch (IOException ioe) {
            throw new InvalidParameterException("Error reading or parsing template file " +
                getParameter(PARAM_BODYTEMPLATE, true) + ":" + ioe.getMessage(), this, null, null);
        }
    }

    protected CompositeParameters createParser(AS2Message msg, OpenAS2Exception exception,
        boolean terminated) {
        CompositeParameters params = new CompositeParameters(true);

        if (msg != null) {
            params.add("message", new MessageParameters(msg));
        } else {
            params.add("message", null);
        }

        if (msg != null && (Object) msg instanceof MessageMDN) {
            params.add("mdn", new MessageMDNParameters((MessageMDN) (Object) msg));
        } else {
            params.add("mdn", null);
        }

        if (exception != null) {
            params.add("exception", new ExceptionParameters(exception, terminated));
        } else {
            params.add("exception", null);
        }

        return params;
    }

    protected void doLog(OpenAS2Exception exception, boolean terminated) {
        try {
            String subject = getParameter(PARAM_SUBJECT, false);

            if (subject == null) {
                subject = getSubject(exception);
            }

            subject = parseText(exception, terminated, subject);

            StringBuffer body = new StringBuffer();

            if (getParameter(PARAM_BODY, false) != null) {
                body.append(parseText(exception, terminated, getParameter(PARAM_BODY, false)));
            }

            body.append("\r\n");

            if (getParameter(PARAM_BODYTEMPLATE, false) != null) {
                body.append(parseText(exception, terminated, getTemplateText()));
            } else {
                body.append(getFormatter().format(exception, terminated));
            }

            sendMessage(subject, body.toString());
        } catch (InvalidParameterException ipe) {
            ipe.printStackTrace();
        }
    }

    protected String parseText(OpenAS2Exception exception, boolean terminated, String text)
        throws InvalidParameterException {
        AS2Message msg = (AS2Message) exception.getSource(OpenAS2Exception.SOURCE_MESSAGE);
        
        CompositeParameters parser = createParser(msg, exception, terminated);
        
        return ParameterParser.parse(text, parser);
        
    }

    protected String parseText(AS2Message msg, String text)
        throws InvalidParameterException {
        CompositeParameters parser = createParser(msg, null, false);

        return ParameterParser.parse(text, parser);
    }

    protected void sendMessage(String subject, String text) {
        Properties props = System.getProperties();
        String oldMailServer = (String) props.get("mail.smtp.host");

        try {
            javax.mail.Session jmSession = javax.mail.Session.getDefaultInstance(System.getProperties());
            MimeMessage m = new MimeMessage(jmSession);
            m.setSender(new InternetAddress(getParameter(PARAM_FROM, true)));
            m.setRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(getParameter(PARAM_TO, true)));

            m.setSentDate(new Date());
            m.setSubject(subject);
            m.setText(text);            
            String mailServer = getParameter(PARAM_SMTPSERVER, true);
            props.put("mail.smtp.host", mailServer);

            Transport.send(m);
        } catch (MessagingException me) {
            me.printStackTrace();
        } catch (InvalidParameterException ipe) {
            ipe.printStackTrace();
        } finally {
            if (oldMailServer != null) {
                props.put("mail.smtp.host", oldMailServer);
            }
        }
    }
}
