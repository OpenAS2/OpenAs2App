package org.openas2;

import org.openas2.app.OpenAS2Server;

import java.io.File;

public class TestPartner {
    private String name;
    private String as2Id;
    private File home;
    private File outbox;
    private File inbox;
    private File sentMDNs;
    private File rxdMDNs;
    private OpenAS2Server server;

    public TestPartner(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public File getHome() {
        return home;
    }

    public String getAs2Id() {
        return as2Id;
    }

    public void setAs2Id(String as2Id) {
        this.as2Id = as2Id;
    }

    public void setHome(File home) {
        this.home = home;
    }

    public File getOutbox() {
        return outbox;
    }

    public void setOutbox(File outbox) {
        this.outbox = outbox;
    }

    public File getInbox() {
        return inbox;
    }

    public void setInbox(File inbox) {
        this.inbox = inbox;
    }

    public File getSentMDNs() {
        return sentMDNs;
    }

    public void setSentMDNs(File sentMDNs) {
        this.sentMDNs = sentMDNs;
    }

    public File getRxdMDNs() {
        return rxdMDNs;
    }

    public void setRxdMDNs(File rxdMDNs) {
        this.rxdMDNs = rxdMDNs;
    }

    public OpenAS2Server getServer() {
        return server;
    }

    public void setServer(OpenAS2Server server) {
        this.server = server;
    }

}
