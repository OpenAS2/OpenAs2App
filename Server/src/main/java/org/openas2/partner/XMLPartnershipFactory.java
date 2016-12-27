package org.openas2.partner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.params.InvalidParameterException;
import org.openas2.util.FileMonitor;
import org.openas2.util.FileMonitorListener;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/** original author unknown
 * 
 * this release added logic to store partnerships and provide methods for partner/partnership command line processor
 * @author joseph mcverry
 *
 */
public class XMLPartnershipFactory extends BasePartnershipFactory
    implements RefreshablePartnershipFactory, FileMonitorListener {
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_INTERVAL = "interval";
    private FileMonitor fileMonitor;
    private Map<String,Object> partners;

	private Log logger = LogFactory.getLog(XMLPartnershipFactory.class.getSimpleName());

    
    public void setFileMonitor(FileMonitor fileMonitor) {
        this.fileMonitor = fileMonitor;
    }

    public FileMonitor getFileMonitor() throws InvalidParameterException {
        boolean createMonitor = ((fileMonitor == null) &&
            (getParameter(PARAM_INTERVAL, false) != null));

        if (!createMonitor && (fileMonitor != null)) {
            String filename = fileMonitor.getFilename();
            createMonitor = ((filename != null) && !filename.equals(getFilename()));
        }

        if (createMonitor) {
            if (fileMonitor != null) {
                fileMonitor.stop();
            }

            int interval = getParameterInt(PARAM_INTERVAL, true);
            File file = new File(getFilename());
            fileMonitor = new FileMonitor(file, interval);
            fileMonitor.addListener(this);
        }

        return fileMonitor;
    }

    public void setFilename(String filename) {
        getParameters().put(PARAM_FILENAME, filename);
    }

    public String getFilename() throws InvalidParameterException {
        return getParameter(PARAM_FILENAME, true);
    }

    public void setPartners(Map<String,Object> map) {
        partners = map;
    }

    public Map<String,Object> getPartners() {
        if (partners == null) {
            partners = new HashMap<String,Object>();
        }

        return partners;
    }

    public void handle(FileMonitor monitor, File file, int eventID) {
        switch (eventID) {
            case FileMonitorListener.EVENT_MODIFIED:

                try {
                    refresh();
					logger.debug("- Partnerships Reloaded -");
                } catch (OpenAS2Exception oae) {
                    oae.terminate();
                }

                break;
        }
    }

    public void init(Session session, Map<String,String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);

        refresh();
    }

    public void refresh() throws OpenAS2Exception {
        try {
            load(new FileInputStream(getFilename()));

            getFileMonitor();
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    protected void load(InputStream in)
        throws ParserConfigurationException, SAXException, IOException, OpenAS2Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        DocumentBuilder parser = factory.newDocumentBuilder();
        Document document = parser.parse(in);
        Element root = document.getDocumentElement();
        NodeList rootNodes = root.getChildNodes();
        Node rootNode;
        String nodeName;

        Map<String,Object> newPartners = new HashMap<String,Object>();
        List<Partnership> newPartnerships = new ArrayList<Partnership>();

        for (int i = 0; i < rootNodes.getLength(); i++) {
            rootNode = rootNodes.item(i);

            nodeName = rootNode.getNodeName();

            if (nodeName.equals("partner")) {
                loadPartner(newPartners, rootNode);
            } else if (nodeName.equals("partnership")) {
                loadPartnership(newPartners, newPartnerships, rootNode);
            }
        }

        synchronized (this) {
            setPartners(newPartners);
            setPartnerships(newPartnerships);
        }
    }

    protected void loadAttributes(Node node, Partnership partnership)
        throws OpenAS2Exception {
        Map<String, String> nodes = XMLUtil.mapAttributeNodes(node.getChildNodes(), "attribute", "name", "value");

        partnership.getAttributes().putAll(nodes);
    }

   public void loadPartner(Map<String,Object> partners, Node node)
        throws OpenAS2Exception {
        String[] requiredAttributes = {"name"};

        Map<String,String> newPartner = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = (String) newPartner.get("name");

        if (partners.get(name) != null) {
            throw new OpenAS2Exception("Partner is defined more than once: " + name);
        }

        partners.put(name, newPartner);
    }

    
   
    protected void loadPartnerIDs(Map<String,Object> partners, String partnershipName, Node partnershipNode,
        String partnerType, Map<String,Object> idMap) throws OpenAS2Exception {
        Node partnerNode = XMLUtil.findChildNode(partnershipNode, partnerType);

        if (partnerNode == null) {
            throw new OpenAS2Exception("Partnership " + partnershipName + " is missing sender");
        }

        Map<String, String> partnerAttr = XMLUtil.mapAttributes(partnerNode);

        // check for a partner name, and look up in partners list if one is found
        String partnerName = (String) partnerAttr.get("name");

        if (partnerName != null) {
            Map<String,Object> map = (Map<String,Object>) partners.get(partnerName);
			Map<String,Object> partner = map;

            if (partner == null) {
                throw new OpenAS2Exception("Partnership " + partnershipName + " has an undefined " +
                    partnerType + ": " + partnerName);
            }

            idMap.putAll(partner);
        }

        // copy all other attributes to the partner id map		
        idMap.putAll(partnerAttr);
    }

    public void loadPartnership(Map<String,Object> partners, List<Partnership> partnerships, Node node)
        throws OpenAS2Exception {
        Partnership partnership = new Partnership();
        String[] requiredAttributes = {"name"};

        Map<String,String> psAttributes = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = (String) psAttributes.get("name");

        if (getPartnership(partnerships, name) != null) {
            throw new OpenAS2Exception("Partnership is defined more than once: " + name);
        }

        partnership.setName(name);

        // load the sender and receiver information
        loadPartnerIDs(partners, name, node, "sender", partnership.getSenderIDs());
        loadPartnerIDs(partners, name, node, "receiver", partnership.getReceiverIDs());

        // read in the partnership attributes
        loadAttributes(node, partnership);

        // add the partnership to the list of available partnerships
        partnerships.add(partnership);
    }
    
    public void storePartnership()
                throws OpenAS2Exception {
    	String fn = getFilename();
    	
    	
    	DecimalFormat df = new DecimalFormat("0000000");
    	long l = 0;
    	File f = null;
    	while (true) {
    		f = new File(fn+'.'+df.format(l));
    		if (f.exists() == false)
    			break;
    		l++;
    	}
    	
    	logger.info("backing up "+ fn +" to "+f.getName());
    	
    	File fr = new File(fn);
    	fr.renameTo(f);
    	
    	try {
			PrintWriter pw = new PrintWriter(new FileOutputStream(fn));

			
    	Map<String,Object> partner = partners;
    	pw.println("<partnerships>");
    	Iterator<Map.Entry<String, Object>> partnerIt = partner.entrySet().iterator();
    	while (partnerIt.hasNext()) {
    		Map.Entry<String, Object> ptrnData = (Map.Entry<String, Object>) partnerIt.next();
    		HashMap<String, Object> partnerMap = (HashMap<String, Object>) ptrnData.getValue();
        	pw.print("  <partner ");
    		Iterator<Map.Entry<String, Object>> attrIt = partnerMap.entrySet().iterator();
    		while (attrIt.hasNext()) {
    			Map.Entry<String, Object> attribute = (Map.Entry<String, Object>) attrIt.next();
            	pw.print(attribute.getKey()+"=\""+attribute.getValue()+"\"");
            	if (attrIt.hasNext())
            		pw.print("\n           ");
    		}
    		pw.println("/>");
    	}
    	List<Partnership> partnerShips = getPartnerships();
    	ListIterator<Partnership> partnerLIt = (ListIterator<Partnership>) partnerShips.listIterator();
    	while (partnerLIt.hasNext()) {
    		Partnership partnership = (Partnership) partnerLIt.next();
    		pw.println("  <partnership name=\""+partnership.getName()+"\">");
    		pw.println("    <sender name=\""+ partnership.getSenderIDs().get("name")+"\"/>");
    		pw.println("    <receiver name=\""+ partnership.getReceiverIDs().get("name")+"\"/>");
    		Map<String,String> partnershipMap = partnership.getAttributes();
    		
    		Iterator<Map.Entry<String,String>> partnershipIt = partnershipMap.entrySet().iterator();
    		while (partnershipIt.hasNext()) {
    			Map.Entry<String,String> partnershipData = (Map.Entry<String,String>) partnershipIt.next();
        			pw.println("    <attribute name=\""+partnershipData.getKey()+"\" value=\""+partnershipData.getValue()+"\"/>" );
    			
    		}
    		pw.println("  </partnership>");
    	}
    	pw.println("</partnerships>");
    	pw.flush();
    	pw.close();
		} catch (FileNotFoundException e) {
			throw new WrappedException(e);
		}
    }
}
