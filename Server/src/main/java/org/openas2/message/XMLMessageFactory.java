package org.openas2.message;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;

import org.openas2.params.InvalidParameterException;
import org.openas2.schedule.HasSchedule;
import org.openas2.support.FileMonitorAdapter;

import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.FileInputStream;

import java.util.HashMap;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * original author unknown
 *
 * @author Cristiam Henriquez
 */
public class XMLMessageFactory extends BaseMessageFactory implements HasSchedule {

    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_INTERVAL = "interval";

    private Document messageXml = null;

    private Map<String, Object> messages;

    private Log logger = LogFactory.getLog(XMLMessageFactory.class.getSimpleName());


    private int getRefreshInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_INTERVAL, false);
    }

    String getFilename() throws InvalidParameterException {
        return getParameter(PARAM_FILENAME, true);
    }

    public Map<String, Object> getMessages() {
        if (messages == null) {
            messages = new HashMap<String, Object>();
        }

        return messages;
    }
    
    private void setMessages(Map<String,Object> map) {
        messages = map;
    }

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        refresh();
    }

    void refresh() throws OpenAS2Exception {
        loadMessageFile();
        refreshConfig();
    }

    void loadMessageFile() throws OpenAS2Exception {
        try (FileInputStream inputStream = new FileInputStream(getFilename())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(inputStream);
            setMessageXml(document);

        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    void refreshConfig() throws OpenAS2Exception {     
        try {
            Element root = getMessageXml().getDocumentElement();
            NodeList rootNodes = root.getChildNodes();
            Node rootNode;
            String nodeName;

            Map<String, Object> newMessages = new HashMap<String, Object>();

            for (int i = 0; i < rootNodes.getLength(); i++) {
                rootNode = rootNodes.item(i);
                nodeName = rootNode.getNodeName();
                if (nodeName.equals("message")) {
                    loadMessage(newMessages, rootNode);
                }
            }

            synchronized (this) {
                setMessages(newMessages);
            }
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    public void loadMessage(Map<String, Object> messages, Node node) throws OpenAS2Exception {
        String[] requiredAttributes = {"msg_id"};
        Map<String, String> newMessage = XMLUtil.mapAttributes(node, requiredAttributes);
        String msg_id = newMessage.get("msg_id");
        messages.put(msg_id,newMessage);
    }

    @Override
    public void schedule(ScheduledExecutorService executor) throws OpenAS2Exception {
        new FileMonitorAdapter() {
            @Override
            public void onConfigFileChanged() throws OpenAS2Exception {
                refresh();
                logger.info("Messages file change detected - Messages Reloaded");
            }
        }.scheduleIfNeed(executor, new File(getFilename()), getRefreshInterval(), TimeUnit.SECONDS);
    }

    public Document getMessageXml() {
        return messageXml;
    }

    public void setMessageXml(Document messageXml) {
        this.messageXml = messageXml;
    }
}