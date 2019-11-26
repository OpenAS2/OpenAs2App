package org.openas2.mail;


class ServerParameters {
    private String userId;
    private String password;
    private String hostName;
    private String port;
    private String mailProtocol;
    private String javaxMailPropFile;

    public String getJavaxMailPropFile() {
        return javaxMailPropFile;
    }

    public ServerParameters setJavaxMailPropFile(String javaxMailPropFile) {
        this.javaxMailPropFile = javaxMailPropFile;
        return this;
    }

    public String getSmtpServer() {
        return hostName;
    }

    public ServerParameters setSmtpServer(String smtpServer) {
        this.hostName = smtpServer;
        return this;
    }

    public String getSmtpPort() {
        return port;
    }

    public ServerParameters setSmtpPort(String smtpPort) {
        this.port = smtpPort;
        return this;
    }

    public String getSmtpProtocol() {
        return mailProtocol;
    }

    public ServerParameters setSmtpProtocol(String smtpProtocol) {
        this.mailProtocol = smtpProtocol;
        return this;
    }
}

