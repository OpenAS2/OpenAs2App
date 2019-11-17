package org.openas2.mail;

class EmailData {
    private String fromDisplay;
    private String from;
    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String body;
    private String template;

    public String getFromDisplay() {
        return fromDisplay;
    }

    public EmailData setFromDisplay(String fromDisplay) {
        this.fromDisplay = fromDisplay;
        return this;
    }

    public String getFrom() {
        return from;
    }

    public EmailData setFrom(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }

    public EmailData setTo(String to) {
        this.to = to;
        return this;
    }

    public String getCc() {
        return cc;
    }

    public EmailData setCc(String cc) {
        this.cc = cc;
        return this;
    }

    public String getBcc() {
        return bcc;
    }

    public EmailData setBcc(String bcc) {
        this.bcc = bcc;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public EmailData setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getBody() {
        return body;
    }

    public EmailData setBody(String body) {
        this.body = body;
        return this;
    }

    public String getTemplate() {
        return template;
    }

    public EmailData setTemplate(String template) {
        this.template = template;
        return this;
    }

}
