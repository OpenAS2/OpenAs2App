package org.openas2.upgrades;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openas2.OpenAS2Exception;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.Partnership;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Migrates a partnerships XML file into the database tables read by
 * {@link org.openas2.partner.DbPartnershipFactory}.
 * <p>
 * The mapping mirrors what each factory does when it loads, so a migrated database produces the same
 * partnerships the XML file produced:
 * <ul>
 * <li>a "partner" element becomes a partner row plus a partner_attribute row for every attribute
 * other than the name, which is held in the partner row itself</li>
 * <li>a "partnership" element becomes a partnership row whose sender and receiver columns point at
 * the partner rows named by its sender and receiver elements</li>
 * <li>any attribute other than the name on those sender and receiver elements is a per-partnership
 * override and becomes a partnership_attribute row in the "sender" or "receiver" category</li>
 * <li>each "attribute" child becomes a partnership_attribute row in the "attribute" category</li>
 * <li>each attribute of the "pollerConfig" child becomes a partnership_attribute row in the
 * "pollerConfig" category</li>
 * </ul>
 * Values are stored exactly as they appear in the file. Placeholders such as
 * $properties.storageBaseDir$ are deliberately left unresolved because both factories resolve them
 * when the partnerships are loaded, so resolving them here would freeze the value of a setting that
 * is meant to stay dynamic.
 * <p>
 * The whole migration runs in one transaction: if anything fails to validate, nothing is written.
 */
public class MigratePartnershipsToDb {

    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 4000;

    /** Counts of what was written, for reporting. */
    public static class Result {
        private int partners;
        private int partnerAttributes;
        private int partnerships;
        private int partnershipAttributes;
        private int senderOverrides;
        private int receiverOverrides;
        private int pollerConfigAttributes;

        public int getPartners() {
            return partners;
        }

        public int getPartnerAttributes() {
            return partnerAttributes;
        }

        public int getPartnerships() {
            return partnerships;
        }

        public int getPartnershipAttributes() {
            return partnershipAttributes;
        }

        public int getSenderOverrides() {
            return senderOverrides;
        }

        public int getReceiverOverrides() {
            return receiverOverrides;
        }

        public int getPollerConfigAttributes() {
            return pollerConfigAttributes;
        }

        public String describe() {
            return partners + " partner(s) with " + partnerAttributes + " attribute(s), "
                    + partnerships + " partnership(s) with " + partnershipAttributes + " attribute(s), "
                    + senderOverrides + " sender override(s), " + receiverOverrides + " receiver override(s), "
                    + pollerConfigAttributes + " poller config attribute(s)";
        }
    }

    /** One partner element read from the file. */
    private static class ParsedPartner {
        private String name;
        private Map<String, String> attributes = new LinkedHashMap<String, String>();
    }

    /** One partnership element read from the file. */
    private static class ParsedPartnership {
        private String name;
        private String senderPartnerName;
        private String receiverPartnerName;
        private Map<String, String> senderOverrides = new LinkedHashMap<String, String>();
        private Map<String, String> receiverOverrides = new LinkedHashMap<String, String>();
        private Map<String, String> attributes = new LinkedHashMap<String, String>();
        private Map<String, String> pollerConfig = new LinkedHashMap<String, String>();
        private boolean hasPollerConfig;
    }

    private final List<ParsedPartner> partners = new ArrayList<ParsedPartner>();
    private final List<ParsedPartnership> partnerships = new ArrayList<ParsedPartnership>();

    /**
     * Reads and validates the partnerships file without touching the database.
     *
     * @param partnershipsFile - the partnerships XML file to migrate
     * @throws OpenAS2Exception if the file cannot be parsed or describes something the schema
     *                          cannot represent
     */
    public void parse(File partnershipsFile) throws OpenAS2Exception {
        Document doc;
        try (InputStream in = new FileInputStream(partnershipsFile)) {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = parser.parse(in);
        } catch (Exception e) {
            throw new OpenAS2Exception("Failed to parse the partnerships file: " + partnershipsFile.getAbsolutePath(), e);
        }
        NodeList nodes = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if ("partner".equals(node.getNodeName())) {
                partners.add(parsePartner(node));
            } else if ("partnership".equals(node.getNodeName())) {
                partnerships.add(parsePartnership(node));
            }
        }
        validate();
    }

    private ParsedPartner parsePartner(Node node) throws OpenAS2Exception {
        ParsedPartner partner = new ParsedPartner();
        NamedNodeMap attribs = node.getAttributes();
        for (int i = 0; i < attribs.getLength(); i++) {
            Node attrib = attribs.item(i);
            if (Partnership.PID_NAME.equals(attrib.getNodeName())) {
                // Held in the partner row itself, not as an attribute row
                partner.name = attrib.getNodeValue();
            } else {
                partner.attributes.put(attrib.getNodeName(), attrib.getNodeValue());
            }
        }
        if (partner.name == null || partner.name.length() == 0) {
            throw new OpenAS2Exception("A partner element has no \"name\" attribute so it cannot be migrated.");
        }
        return partner;
    }

    private ParsedPartnership parsePartnership(Node node) throws OpenAS2Exception {
        ParsedPartnership partnership = new ParsedPartnership();
        Node nameNode = node.getAttributes().getNamedItem(Partnership.PID_NAME);
        if (nameNode == null || nameNode.getNodeValue().length() == 0) {
            throw new OpenAS2Exception("A partnership element has no \"name\" attribute so it cannot be migrated.");
        }
        partnership.name = nameNode.getNodeValue();

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            String childName = child.getNodeName();
            if (Partnership.PTYPE_SENDER.equals(childName)) {
                partnership.senderPartnerName = readPartnerRef(partnership, child, partnership.senderOverrides);
            } else if (Partnership.PTYPE_RECEIVER.equals(childName)) {
                partnership.receiverPartnerName = readPartnerRef(partnership, child, partnership.receiverOverrides);
            } else if ("attribute".equals(childName)) {
                Node attrName = child.getAttributes().getNamedItem("name");
                Node attrValue = child.getAttributes().getNamedItem("value");
                if (attrName == null) {
                    throw new OpenAS2Exception("Partnership \"" + partnership.name + "\" has an attribute element with no \"name\".");
                }
                partnership.attributes.put(attrName.getNodeValue(), attrValue == null ? "" : attrValue.getNodeValue());
            } else if (Partnership.PCFG_POLLER.equals(childName)) {
                partnership.hasPollerConfig = true;
                NamedNodeMap pollerAttribs = child.getAttributes();
                for (int j = 0; j < pollerAttribs.getLength(); j++) {
                    Node attrib = pollerAttribs.item(j);
                    partnership.pollerConfig.put(attrib.getNodeName(), attrib.getNodeValue());
                }
            }
        }
        if (partnership.senderPartnerName == null) {
            throw new OpenAS2Exception("Partnership \"" + partnership.name + "\" has no " + Partnership.PTYPE_SENDER
                    + " element naming a partner. The database schema requires every partnership to reference a partner row"
                    + " for its sender and receiver, so give the element a \"name\" that matches a partner and re-run.");
        }
        if (partnership.receiverPartnerName == null) {
            throw new OpenAS2Exception("Partnership \"" + partnership.name + "\" has no " + Partnership.PTYPE_RECEIVER
                    + " element naming a partner. The database schema requires every partnership to reference a partner row"
                    + " for its sender and receiver, so give the element a \"name\" that matches a partner and re-run.");
        }
        return partnership;
    }

    /**
     * Reads a sender or receiver element: the "name" identifies the partner row to point at and every
     * other attribute is an override stored against the partnership.
     */
    private String readPartnerRef(ParsedPartnership partnership, Node node, Map<String, String> overrides) {
        String partnerName = null;
        NamedNodeMap attribs = node.getAttributes();
        for (int i = 0; i < attribs.getLength(); i++) {
            Node attrib = attribs.item(i);
            if (Partnership.PID_NAME.equals(attrib.getNodeName())) {
                partnerName = attrib.getNodeValue();
            } else {
                overrides.put(attrib.getNodeName(), attrib.getNodeValue());
            }
        }
        return partnerName;
    }

    private void validate() throws OpenAS2Exception {
        List<String> partnerNames = new ArrayList<String>();
        for (ParsedPartner partner : partners) {
            if (partnerNames.contains(partner.name)) {
                throw new OpenAS2Exception("Partner is defined more than once: " + partner.name);
            }
            partnerNames.add(partner.name);
            checkValueLengths("partner \"" + partner.name + "\"", partner.attributes);
        }
        List<String> partnershipNames = new ArrayList<String>();
        for (ParsedPartnership partnership : partnerships) {
            if (partnershipNames.contains(partnership.name)) {
                throw new OpenAS2Exception("Partnership is defined more than once: " + partnership.name);
            }
            partnershipNames.add(partnership.name);
            if (!partnerNames.contains(partnership.senderPartnerName)) {
                throw new OpenAS2Exception("Partnership \"" + partnership.name + "\" has an undefined "
                        + Partnership.PTYPE_SENDER + ": " + partnership.senderPartnerName);
            }
            if (!partnerNames.contains(partnership.receiverPartnerName)) {
                throw new OpenAS2Exception("Partnership \"" + partnership.name + "\" has an undefined "
                        + Partnership.PTYPE_RECEIVER + ": " + partnership.receiverPartnerName);
            }
            String where = "partnership \"" + partnership.name + "\"";
            checkValueLengths(where, partnership.attributes);
            checkValueLengths(where + " " + Partnership.PTYPE_SENDER, partnership.senderOverrides);
            checkValueLengths(where + " " + Partnership.PTYPE_RECEIVER, partnership.receiverOverrides);
            checkValueLengths(where + " " + Partnership.PCFG_POLLER, partnership.pollerConfig);
        }
    }

    private void checkValueLengths(String where, Map<String, String> attributes) throws OpenAS2Exception {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getValue() != null && entry.getValue().length() > MAX_ATTRIBUTE_VALUE_LENGTH) {
                throw new OpenAS2Exception("The value of \"" + entry.getKey() + "\" on " + where + " is "
                        + entry.getValue().length() + " characters, which exceeds the "
                        + MAX_ATTRIBUTE_VALUE_LENGTH + " character attribute value column. Shorten it or widen the column.");
            }
        }
    }

    /**
     * Writes the parsed partnerships to the database in a single transaction.
     *
     * @param conn - an open connection to the database holding the partner and partnership tables
     * @param replaceExisting - true to delete the partner and partnership rows already present first.
     *                          When false the migration aborts if any are present, so an accidental
     *                          second run cannot merge two configurations together.
     * @return counts of what was written
     * @throws OpenAS2Exception if the tables are missing, already populated, or the write fails
     */
    public Result write(Connection conn, boolean replaceExisting) throws OpenAS2Exception {
        Result result = new Result();
        boolean originalAutoCommit;
        try {
            originalAutoCommit = conn.getAutoCommit();
        } catch (SQLException e) {
            throw new OpenAS2Exception("Failed to read the connection state.", e);
        }
        try {
            conn.setAutoCommit(false);
            try {
                if (replaceExisting) {
                    // Child tables first: the partnership and partner rows are referenced by foreign keys
                    execute(conn, "DELETE FROM partnership_attribute");
                    execute(conn, "DELETE FROM partnership");
                    execute(conn, "DELETE FROM partner_attribute");
                    execute(conn, "DELETE FROM partner");
                } else {
                    requireEmpty(conn, "partner");
                    requireEmpty(conn, "partnership");
                }
                Map<String, Long> partnerIds = insertPartners(conn, result);
                insertPartnerships(conn, partnerIds, result);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (OpenAS2Exception e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAS2Exception("Failed to write the partnerships to the database.", e);
        } finally {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                // Nothing useful to do: the migration has already committed or rolled back
            }
        }
        return result;
    }

    private Map<String, Long> insertPartners(Connection conn, Result result) throws Exception {
        Map<String, Long> partnerIds = new LinkedHashMap<String, Long>();
        for (ParsedPartner partner : partners) {
            try (PreparedStatement s = conn.prepareStatement("INSERT INTO partner (NAME) VALUES (?)")) {
                s.setString(1, partner.name);
                s.executeUpdate();
            }
            Long id = findId(conn, "SELECT ID FROM partner WHERE NAME = ?", partner.name);
            if (id == null) {
                throw new OpenAS2Exception("Failed to read back the inserted partner: " + partner.name);
            }
            partnerIds.put(partner.name, id);
            result.partners++;
            try (PreparedStatement s = conn.prepareStatement(
                    "INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> attribute : partner.attributes.entrySet()) {
                    s.setLong(1, id);
                    s.setString(2, attribute.getKey());
                    s.setString(3, attribute.getValue());
                    s.executeUpdate();
                    result.partnerAttributes++;
                }
            }
        }
        return partnerIds;
    }

    private void insertPartnerships(Connection conn, Map<String, Long> partnerIds, Result result) throws Exception {
        for (ParsedPartnership partnership : partnerships) {
            try (PreparedStatement s = conn.prepareStatement(
                    "INSERT INTO partnership (NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (?, ?, ?)")) {
                s.setString(1, partnership.name);
                s.setLong(2, partnerIds.get(partnership.senderPartnerName));
                s.setLong(3, partnerIds.get(partnership.receiverPartnerName));
                s.executeUpdate();
            }
            Long id = findId(conn, "SELECT ID FROM partnership WHERE NAME = ?", partnership.name);
            if (id == null) {
                throw new OpenAS2Exception("Failed to read back the inserted partnership: " + partnership.name);
            }
            result.partnerships++;
            result.partnershipAttributes += insertAttributes(conn, id, DbPartnershipFactory.CATEGORY_ATTRIBUTE, partnership.attributes);
            result.senderOverrides += insertAttributes(conn, id, DbPartnershipFactory.CATEGORY_SENDER, partnership.senderOverrides);
            result.receiverOverrides += insertAttributes(conn, id, DbPartnershipFactory.CATEGORY_RECEIVER, partnership.receiverOverrides);
            result.pollerConfigAttributes += insertAttributes(conn, id, DbPartnershipFactory.CATEGORY_POLLER_CONFIG, partnership.pollerConfig);
        }
    }

    private int insertAttributes(Connection conn, long partnershipId, String category, Map<String, String> attributes) throws Exception {
        if (attributes.isEmpty()) {
            return 0;
        }
        int count = 0;
        try (PreparedStatement s = conn.prepareStatement(
                "INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                s.setLong(1, partnershipId);
                s.setString(2, category);
                s.setString(3, attribute.getKey());
                s.setString(4, attribute.getValue());
                s.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private void requireEmpty(Connection conn, String table) throws Exception {
        try (PreparedStatement s = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = s.executeQuery()) {
            rs.next();
            long count = rs.getLong(1);
            if (count > 0) {
                throw new OpenAS2Exception("The \"" + table + "\" table already contains " + count
                        + " row(s). Migrating now would merge two configurations together. Empty the partner and"
                        + " partnership tables first, or re-run with --replace to have this tool delete them.");
            }
        }
    }

    private void execute(Connection conn, String sql) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            s.executeUpdate();
        }
    }

    private Long findId(Connection conn, String sql, String key) throws Exception {
        try (PreparedStatement s = conn.prepareStatement(sql)) {
            s.setString(1, key);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /**
     * Describes what would be written, for a dry run.
     */
    public String describeParsed() {
        StringBuilder buf = new StringBuilder();
        buf.append("Partners (").append(partners.size()).append("):").append(System.lineSeparator());
        for (ParsedPartner partner : partners) {
            buf.append("  ").append(partner.name).append(" -> ").append(partner.attributes.size())
                    .append(" attribute(s): ").append(partner.attributes.keySet()).append(System.lineSeparator());
        }
        buf.append("Partnerships (").append(partnerships.size()).append("):").append(System.lineSeparator());
        for (ParsedPartnership partnership : partnerships) {
            buf.append("  ").append(partnership.name)
                    .append(" [").append(partnership.senderPartnerName).append(" -> ").append(partnership.receiverPartnerName).append("]")
                    .append(" attributes=").append(partnership.attributes.size())
                    .append(" senderOverrides=").append(partnership.senderOverrides.size())
                    .append(" receiverOverrides=").append(partnership.receiverOverrides.size())
                    .append(" pollerConfig=").append(partnership.hasPollerConfig ? String.valueOf(partnership.pollerConfig.size()) : "none")
                    .append(System.lineSeparator());
        }
        return buf.toString();
    }

    private static void usage() {
        System.out.println("Migrates a partnerships XML file into the partner and partnership database tables");
        System.out.println("read by org.openas2.partner.DbPartnershipFactory.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  MigratePartnershipsToDb <partnerships.xml> <jdbc_url> <db_user> <db_password> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --dry-run              Parse and validate the file and print what would be written. Changes nothing.");
        System.out.println("  --replace              Delete the partner and partnership rows already in the database first.");
        System.out.println("                         Without this the migration aborts if either table has any rows.");
        System.out.println("  --jdbc-driver=<class>  Load this JDBC driver class before connecting. Only needed for a driver");
        System.out.println("                         that does not register itself automatically.");
        System.out.println();
        System.out.println("The tables must already exist. Create them with the partner and partnership section of");
        System.out.println("config/db_ddl.sql (or resources/db/openas2-schema.xml) before running this. Copy out just");
        System.out.println("that section: running the whole file drops the msg_metadata message tracking table.");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  MigratePartnershipsToDb /opt/OpenAS2/config/partnerships.xml \\");
        System.out.println("      \"jdbc:h2:/opt/OpenAS2/data/openas2\" sa OpenAS2 --dry-run");
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            usage();
            System.exit(args == null || args.length < 1 ? 1 : 0);
            return;
        }
        boolean dryRun = false;
        boolean replace = false;
        String jdbcDriver = null;
        List<String> positional = new ArrayList<String>();
        for (String arg : args) {
            if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if ("--replace".equals(arg)) {
                replace = true;
            } else if (arg.startsWith("--jdbc-driver=")) {
                jdbcDriver = arg.substring("--jdbc-driver=".length());
            } else if (arg.startsWith("--")) {
                System.err.println("Unknown option: " + arg);
                usage();
                System.exit(1);
                return;
            } else {
                positional.add(arg);
            }
        }
        int required = dryRun ? 1 : 4;
        if (positional.size() < required) {
            System.err.println(dryRun
                    ? "A dry run requires the partnerships file."
                    : "Requires the partnerships file, JDBC URL, database user and password.");
            usage();
            System.exit(1);
            return;
        }
        File partnershipsFile = new File(positional.get(0));
        if (!partnershipsFile.isFile()) {
            System.err.println("No partnerships file found at: " + partnershipsFile.getAbsolutePath());
            System.exit(1);
            return;
        }

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        try {
            migrator.parse(partnershipsFile);
            System.out.println("Parsed " + partnershipsFile.getAbsolutePath() + ":");
            System.out.println(migrator.describeParsed());
            if (dryRun) {
                System.out.println("Dry run: nothing was written to any database.");
                return;
            }
            if (jdbcDriver != null) {
                Class.forName(jdbcDriver);
            }
            try (Connection conn = DriverManager.getConnection(positional.get(1), positional.get(2), positional.get(3))) {
                Result result = migrator.write(conn, replace);
                System.out.println("Migration complete: " + result.describe());
                System.out.println();
                System.out.println("Now point the partnerships component in config.xml at the database by replacing the");
                System.out.println("XMLPartnershipFactory element with the commented DbPartnershipFactory example, then");
                System.out.println("restart and check the partnership list matches the XML file it came from.");
            }
        } catch (OpenAS2Exception e) {
            // A validation or state problem the operator can act on: the message is the useful part
            System.err.println("Migration failed and nothing was written: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Migration failed unexpectedly and nothing was written: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
