package org.openas2.partner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.XMLSession;
import org.openas2.params.ComponentParameters;
import org.openas2.params.CompositeParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.ParameterParser;
import org.openas2.processor.msgtracking.EmbeddedDBHandler;
import org.openas2.processor.msgtracking.ExternalDBHandler;
import org.openas2.processor.msgtracking.IDBHandler;
import org.openas2.schedule.HasSchedule;
import org.openas2.util.AS2Util;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A partnership factory that stores partners and partnerships in a database instead of
 * the partnerships XML file. Any database with a JDBC driver is supported (including the
 * embedded H2 database and external databases such as Azure SQL, PostgreSQL, MySQL and
 * Oracle) using the same connection parameters as the DbTrackingModule.
 * <p>
 * Partnerships can be managed directly in the database by external tooling or through
 * the partner/partnership console and REST API commands. The in-memory state is loaded
 * at startup, after every change made through this factory, on the "refresh" console
 * command and (if the "interval" parameter is set) on a fixed schedule.
 * <p>
 * Table structure (see Server/src/resources/db/openas2-schema.xml and db_ddl.sql):
 * <ul>
 * <li>partner - one row per partner with a unique name</li>
 * <li>partner_attribute - name/value pairs per partner (as2_id, x509_alias, email ...)</li>
 * <li>partnership - one row per partnership linking a sender and receiver partner</li>
 * <li>partnership_attribute - name/value pairs per partnership. The category column is
 * "attribute" for regular partnership attributes or "sender"/"receiver" for attribute
 * overrides applied on top of the linked partner's attributes for this partnership only</li>
 * </ul>
 */
public class DbPartnershipFactory extends BasePartnershipFactory implements RefreshablePartnershipFactory, HasSchedule {

    public static final String PARAM_DB_USER = "db_user";
    public static final String PARAM_DB_PWD = "db_pwd";
    public static final String PARAM_JDBC_CONNECT_STRING = "jdbc_connect_string";
    public static final String PARAM_JDBC_DRIVER = "jdbc_driver";
    public static final String PARAM_FORCE_LOAD_JDBC_DRIVER = "force_load_jdbc_driver";
    public static final String PARAM_USE_EMBEDDED_DB = "use_embedded_db";
    public static final String PARAM_INTERVAL = "interval";

    public static final String CATEGORY_ATTRIBUTE = "attribute";
    public static final String CATEGORY_SENDER = Partnership.PTYPE_SENDER;
    public static final String CATEGORY_RECEIVER = Partnership.PTYPE_RECEIVER;
    public static final String CATEGORY_POLLER_CONFIG = Partnership.PCFG_POLLER;

    private IDBHandler dbHandler = null;

    private Map<String, Object> partners = new HashMap<String, Object>();

    // Set once init() completes so refreshes triggered at runtime restart the partnership
    // pollers (at startup Session.start() does that after the whole system is wired up)
    private boolean initialized = false;

    private Logger logger = LoggerFactory.getLogger(DbPartnershipFactory.class);

    public Map<String, Object> getPartners() {
        return partners;
    }

    private int getRefreshInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_INTERVAL, false);
    }

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);

        CompositeParameters paramParser = new CompositeParameters(true);
        paramParser.add("component", new ComponentParameters(this));
        // Support component attributes in connect string
        String jdbcConnectString = ParameterParser.parse(getParameter(PARAM_JDBC_CONNECT_STRING, true), paramParser);

        boolean useEmbeddedDB = "true".equals(getParameter(PARAM_USE_EMBEDDED_DB, "false"));
        if (!useEmbeddedDB && "true".equals(getParameter(PARAM_FORCE_LOAD_JDBC_DRIVER, "false"))) {
            String jdbcDriver = getParameter(PARAM_JDBC_DRIVER, true);
            try {
                Class.forName(jdbcDriver);
            } catch (ClassNotFoundException e) {
                throw new OpenAS2Exception("Failed to load JDBC driver for partnership factory: " + jdbcDriver, e);
            }
        }
        if (useEmbeddedDB) {
            dbHandler = new EmbeddedDBHandler();
        } else {
            dbHandler = new ExternalDBHandler();
        }
        dbHandler.start(jdbcConnectString, getParameter(PARAM_DB_USER, true), getParameter(PARAM_DB_PWD, true), getParameters());

        refresh();
        initialized = true;
    }

    public void refresh() throws OpenAS2Exception {
        Session session = getSession();
        if (session != null) {
            session.destroyPartnershipPollers(Session.PARTNERSHIP_POLLER);
        }
        try (Connection conn = dbHandler.getConnection()) {
            Map<Long, Map<String, String>> partnersById = loadPartners(conn);
            Map<String, Object> newPartners = new HashMap<String, Object>();
            for (Map<String, String> partner : partnersById.values()) {
                String name = partner.get(Partnership.PID_NAME);
                if (newPartners.put(name, partner) != null) {
                    throw new OpenAS2Exception("Partner is defined more than once: " + name);
                }
            }
            List<Partnership> newPartnerships = loadPartnerships(conn, partnersById);

            synchronized (this) {
                this.partners = newPartners;
                setPartnerships(newPartnerships);
            }
            if (logger.isInfoEnabled()) {
                logger.info("Loaded " + newPartners.size() + " partners and " + newPartnerships.size() + " partnerships from database");
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        // At startup Session.start() starts the pollers once everything is wired up; for
        // runtime refreshes the destroy/reload above leaves them stopped so restart them here
        if (initialized && session != null) {
            session.startPartnershipPollers();
        }
    }

    // Keyed by partner ID so partnerships can resolve their sender/receiver rows
    private Map<Long, Map<String, String>> loadPartners(Connection conn) throws Exception {
        Map<Long, Map<String, String>> partnersById = new HashMap<Long, Map<String, String>>();
        try (PreparedStatement s = conn.prepareStatement("SELECT ID, NAME FROM partner");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                Map<String, String> partner = new HashMap<String, String>();
                partner.put(Partnership.PID_NAME, rs.getString(2));
                partnersById.put(rs.getLong(1), partner);
            }
        }
        try (PreparedStatement s = conn.prepareStatement("SELECT PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE FROM partner_attribute");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                Map<String, String> partner = partnersById.get(rs.getLong(1));
                if (partner == null) {
                    throw new OpenAS2Exception("Partner attribute references unknown partner ID: " + rs.getLong(1));
                }
                partner.put(rs.getString(2), rs.getString(3));
            }
        }
        return partnersById;
    }

    private List<Partnership> loadPartnerships(Connection conn, Map<Long, Map<String, String>> partnersById) throws Exception {
        List<Partnership> newPartnerships = new ArrayList<Partnership>();
        Map<Long, Partnership> partnershipsById = new HashMap<Long, Partnership>();
        try (PreparedStatement s = conn.prepareStatement("SELECT ID, NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID FROM partnership");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                String name = rs.getString(2);
                if (getPartnership(newPartnerships, name) != null) {
                    throw new OpenAS2Exception("Partnership is defined more than once: " + name);
                }
                Partnership partnership = new Partnership();
                partnership.setName(name);
                Map<String, String> sender = partnersById.get(rs.getLong(3));
                if (sender == null) {
                    throw new OpenAS2Exception("Partnership \"" + name + "\" references an unknown sender partner ID: " + rs.getLong(3));
                }
                partnership.getSenderIDs().putAll(sender);
                Map<String, String> receiver = partnersById.get(rs.getLong(4));
                if (receiver == null) {
                    throw new OpenAS2Exception("Partnership \"" + name + "\" references an unknown receiver partner ID: " + rs.getLong(4));
                }
                partnership.getReceiverIDs().putAll(receiver);
                partnershipsById.put(id, partnership);
                newPartnerships.add(partnership);
            }
        }
        // Partnership attributes: plain attributes, per-partnership sender/receiver overrides
        // and partnership poller configurations
        Map<Long, Map<String, String>> attributesById = new HashMap<Long, Map<String, String>>();
        Map<Long, Map<String, String>> pollerConfigsById = new HashMap<Long, Map<String, String>>();
        try (PreparedStatement s = conn.prepareStatement("SELECT PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE FROM partnership_attribute");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                long partnershipId = rs.getLong(1);
                Partnership partnership = partnershipsById.get(partnershipId);
                if (partnership == null) {
                    throw new OpenAS2Exception("Partnership attribute references unknown partnership ID: " + partnershipId);
                }
                String category = rs.getString(2);
                String attrName = rs.getString(3);
                String attrValue = rs.getString(4);
                if (CATEGORY_SENDER.equalsIgnoreCase(category)) {
                    partnership.getSenderIDs().put(attrName, attrValue);
                } else if (CATEGORY_RECEIVER.equalsIgnoreCase(category)) {
                    partnership.getReceiverIDs().put(attrName, attrValue);
                } else if (CATEGORY_ATTRIBUTE.equalsIgnoreCase(category)) {
                    Map<String, String> attributes = attributesById.get(partnershipId);
                    if (attributes == null) {
                        attributes = new HashMap<String, String>();
                        attributesById.put(partnershipId, attributes);
                    }
                    attributes.put(attrName, attrValue);
                } else if (CATEGORY_POLLER_CONFIG.equalsIgnoreCase(category)) {
                    Map<String, String> pollerConfig = pollerConfigsById.get(partnershipId);
                    if (pollerConfig == null) {
                        pollerConfig = new HashMap<String, String>();
                        pollerConfigsById.put(partnershipId, pollerConfig);
                    }
                    pollerConfig.put(attrName, attrValue);
                } else {
                    throw new OpenAS2Exception("Unsupported partnership attribute category \"" + category + "\" for partnership: " + partnership.getName());
                }
            }
        }
        for (Map.Entry<Long, Map<String, String>> entry : attributesById.entrySet()) {
            Partnership partnership = partnershipsById.get(entry.getKey());
            Map<String, String> attributes = entry.getValue();
            AS2Util.attributeEnhancer(attributes);
            partnership.getAttributes().putAll(attributes);
        }
        for (Partnership partnership : newPartnerships) {
            if ("true".equalsIgnoreCase(partnership.getAttributeOrProperty(Partnership.PA_USE_DYNAMIC_CONTENT_TYPE_MAPPING, "false"))) {
                try {
                    partnership.setUseDynamicContentTypeLookup(true);
                } catch (IOException e) {
                    logger.error("Error setting up dynamic Content-Type lookup: " + e.getMessage(), e);
                    throw new OpenAS2Exception("Partnership failed to be set up correctly for dynamic Content-Type lookup: " + partnership.getName());
                }
            }
        }
        for (Map.Entry<Long, Map<String, String>> entry : pollerConfigsById.entrySet()) {
            Partnership partnership = partnershipsById.get(entry.getKey());
            launchPartnershipPoller(partnership, entry.getValue());
            // Surface the poller config in the view/list command output the same way the
            // view command renders the pollerConfig element of the XML partnerships file
            entry.getValue().forEach((key, value) -> partnership.getAttributes().put("pollerConfig." + key, value));
        }
        return newPartnerships;
    }

    /**
     * Registers a directory poller for a partnership by merging its "pollerConfig" category
     * attributes over the base poller config from config.xml, mirroring the pollerConfig
     * element support of the XML partnerships file.
     */
    private void launchPartnershipPoller(Partnership partnership, Map<String, String> pollerConfig) throws OpenAS2Exception {
        if (!"true".equalsIgnoreCase(pollerConfig.get("enabled"))) {
            return;
        }
        Session session = getSession();
        if (!(session instanceof XMLSession)) {
            logger.warn("Cannot launch a partnership poller without a session for partnership: " + partnership.getName());
            return;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Found partnership poller for partnership: " + partnership.getName());
        }
        Node basePollerConfigNode = ((XMLSession) session).getBasePartnershipPollerConfig();
        if (basePollerConfigNode == null) {
            throw new OpenAS2Exception("Missing base poller config node in config.xml to configure partnership poller.");
        }
        Document pollerDoc;
        try {
            pollerDoc = XMLUtil.createDoc(basePollerConfigNode);
        } catch (Exception e) {
            throw new OpenAS2Exception("Failed to create a poller document: " + e.getMessage(), e);
        }
        Element pollerConfigElem = pollerDoc.getDocumentElement();
        // Merge the attributes from the base config with the partnership specific ones
        Map<String, String> attributes = XMLUtil.mapAttributes(pollerConfigElem);
        attributes.putAll(pollerConfig);
        // Enhance the attribute values in case they are using dynamic variables
        AS2Util.attributeEnhancer(attributes);
        attributes.forEach((key, value) -> {
            pollerConfigElem.setAttribute(key, value);
        });
        // replace the $partnership.* placeholders
        replacePartnershipPlaceHolders(pollerDoc, partnership);
        session.loadPartnershipPoller(pollerConfigElem, partnership.getName(), Session.PARTNERSHIP_POLLER);
    }

    /**
     * Adds a partner to the database and reloads the in-memory partnership state.
     *
     * @param attributes - partner attributes including the mandatory "name" attribute
     * @throws OpenAS2Exception if the partner has no name or already exists
     */
    public synchronized void addPartner(Map<String, String> attributes) throws OpenAS2Exception {
        String name = attributes.get(Partnership.PID_NAME);
        if (name == null || name.trim().length() == 0) {
            throw new OpenAS2Exception("Partner must have a name");
        }
        try (Connection conn = dbHandler.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (findId(conn, "SELECT ID FROM partner WHERE NAME = ?", name) != null) {
                    throw new OpenAS2Exception("Partner is defined more than once: " + name);
                }
                try (PreparedStatement s = conn.prepareStatement("INSERT INTO partner (NAME) VALUES (?)")) {
                    s.setString(1, name);
                    s.executeUpdate();
                }
                long partnerId = findId(conn, "SELECT ID FROM partner WHERE NAME = ?", name);
                try (PreparedStatement s = conn.prepareStatement("INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                        if (Partnership.PID_NAME.equals(attribute.getKey())) {
                            continue;
                        }
                        s.setLong(1, partnerId);
                        s.setString(2, attribute.getKey());
                        s.setString(3, attribute.getValue());
                        s.executeUpdate();
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        refresh();
    }

    /**
     * Deletes a partner from the database and reloads the in-memory partnership state.
     *
     * @param name - the name of the partner to delete
     * @throws OpenAS2Exception if the partner does not exist or is referenced by a partnership
     */
    public synchronized void deletePartner(String name) throws OpenAS2Exception {
        try (Connection conn = dbHandler.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long id = findId(conn, "SELECT ID FROM partner WHERE NAME = ?", name);
                if (id == null) {
                    throw new OpenAS2Exception("Unknown partner name: " + name);
                }
                try (PreparedStatement s = conn.prepareStatement("SELECT COUNT(*) FROM partnership WHERE SENDER_PARTNER_ID = ? OR RECEIVER_PARTNER_ID = ?")) {
                    s.setLong(1, id);
                    s.setLong(2, id);
                    try (ResultSet rs = s.executeQuery()) {
                        rs.next();
                        if (rs.getLong(1) > 0) {
                            throw new OpenAS2Exception("Cannot delete partner; it is tied to some partnerships");
                        }
                    }
                }
                executeUpdate(conn, "DELETE FROM partner_attribute WHERE PARTNER_ID = ?", id);
                executeUpdate(conn, "DELETE FROM partner WHERE ID = ?", id);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        refresh();
    }

    /**
     * Adds a partnership between two existing partners to the database and reloads the
     * in-memory partnership state.
     *
     * @param name         - unique name of the partnership
     * @param senderName   - name of an existing partner acting as the sender
     * @param receiverName - name of an existing partner acting as the receiver
     * @param attributes   - partnership attributes (stored with the "attribute" category)
     * @throws OpenAS2Exception if the partnership already exists or either partner is unknown
     */
    public synchronized void addPartnership(String name, String senderName, String receiverName, Map<String, String> attributes) throws OpenAS2Exception {
        addPartnership(name, senderName, receiverName, attributes, null);
    }

    /**
     * Adds a partnership between two existing partners to the database and reloads the
     * in-memory partnership state.
     *
     * @param name         - unique name of the partnership
     * @param senderName   - name of an existing partner acting as the sender
     * @param receiverName - name of an existing partner acting as the receiver
     * @param attributes   - partnership attributes (stored with the "attribute" category)
     * @param pollerConfig - directory poller attributes for this partnership (stored with the
     *                       "pollerConfig" category), or null if the partnership has no poller
     * @throws OpenAS2Exception if the partnership already exists or either partner is unknown
     */
    public synchronized void addPartnership(String name, String senderName, String receiverName, Map<String, String> attributes, Map<String, String> pollerConfig) throws OpenAS2Exception {
        try (Connection conn = dbHandler.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (findId(conn, "SELECT ID FROM partnership WHERE NAME = ?", name) != null) {
                    throw new OpenAS2Exception("Partnership is defined more than once: " + name);
                }
                Long senderId = findId(conn, "SELECT ID FROM partner WHERE NAME = ?", senderName);
                if (senderId == null) {
                    throw new OpenAS2Exception("Partnership " + name + " has an undefined " + Partnership.PTYPE_SENDER + ": " + senderName);
                }
                Long receiverId = findId(conn, "SELECT ID FROM partner WHERE NAME = ?", receiverName);
                if (receiverId == null) {
                    throw new OpenAS2Exception("Partnership " + name + " has an undefined " + Partnership.PTYPE_RECEIVER + ": " + receiverName);
                }
                try (PreparedStatement s = conn.prepareStatement("INSERT INTO partnership (NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (?, ?, ?)")) {
                    s.setString(1, name);
                    s.setLong(2, senderId);
                    s.setLong(3, receiverId);
                    s.executeUpdate();
                }
                long partnershipId = findId(conn, "SELECT ID FROM partnership WHERE NAME = ?", name);
                try (PreparedStatement s = conn.prepareStatement("INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                        s.setLong(1, partnershipId);
                        s.setString(2, CATEGORY_ATTRIBUTE);
                        s.setString(3, attribute.getKey());
                        s.setString(4, attribute.getValue());
                        s.executeUpdate();
                    }
                    if (pollerConfig != null) {
                        for (Map.Entry<String, String> attribute : pollerConfig.entrySet()) {
                            s.setLong(1, partnershipId);
                            s.setString(2, CATEGORY_POLLER_CONFIG);
                            s.setString(3, attribute.getKey());
                            s.setString(4, attribute.getValue());
                            s.executeUpdate();
                        }
                    }
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        refresh();
    }

    /**
     * Deletes a partnership from the database and reloads the in-memory partnership state.
     *
     * @param name - the name of the partnership to delete
     * @throws OpenAS2Exception if the partnership does not exist
     */
    public synchronized void deletePartnership(String name) throws OpenAS2Exception {
        try (Connection conn = dbHandler.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long id = findId(conn, "SELECT ID FROM partnership WHERE NAME = ?", name);
                if (id == null) {
                    throw new OpenAS2Exception("Partnership not found: " + name);
                }
                executeUpdate(conn, "DELETE FROM partnership_attribute WHERE PARTNERSHIP_ID = ?", id);
                executeUpdate(conn, "DELETE FROM partnership WHERE ID = ?", id);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        refresh();
    }

    private Long findId(Connection conn, String sql, String name) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            s.setString(1, name);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private void executeUpdate(Connection conn, String sql, long id) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            s.setLong(1, id);
            s.executeUpdate();
        }
    }

    @Override
    public void schedule(ScheduledExecutorService executor) throws OpenAS2Exception {
        int interval = getRefreshInterval();
        if (interval <= 0) {
            return;
        }
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    refresh();
                } catch (Exception e) {
                    logger.error("Failed to refresh partnerships from database: " + e.getMessage(), e);
                }
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() throws Exception {
        if (dbHandler != null) {
            dbHandler.stop();
            dbHandler = null;
        }
    }
}
