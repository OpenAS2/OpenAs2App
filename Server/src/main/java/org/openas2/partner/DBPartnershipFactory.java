package org.openas2.partner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DBFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.XMLSession;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.lib.partner.IPartner;
import org.openas2.params.InvalidParameterException;
import org.openas2.support.FileMonitor;
import org.openas2.support.FileMonitorListener;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Node;


/** 
 * @author Luc Guinchard
 *
 */
public class DBPartnershipFactory extends BasePartnershipFactory 
	implements RefreshablePartnershipFactory {
	private static final Log LOGGER = LogFactory.getLog(DBPartnershipFactory.class.getSimpleName());
	public static final String PARAM_INTERVAL = "interval";
	public static final String PARAM_TABLE_PARTNER = "table_partner";
	public static final String PARAM_TABLE_PARTNERSHIP = "table_partnership";
	public static final String PARAM_PARTNER_FIELD_NAME = "partnerfield_name";
	public static final String PARAM_PARTNER_FIELD_AS2ID = "partnerfield_as2_id";
	public static final String PARAM_PARTNER_FIELD_X509ALIAS = "partnerfield_x509_alias";
	public static final String PARAM_PARTNER_FIELD_EMAIL = "partnerfield_email";
	public static final String PARAM_PARTNERSHIP_FIELD_NAME = "partnerfield_name";
	public static final String PARAM_PARTNERSHIP_FIELD_SENDER = "partnerfield_sender";
	public static final String PARAM_PARTNERSHIP_FIELD_RECEIVER = "partnerfield_receiver";
	public static final String PARAM_PARTNERSHIP_FIELD_SIGN = "partnerfield_sign";
	public static final String PARAM_PARTNERSHIP_FIELD_ENCRYPT = "partnerfield_encrypt";
	public static final String PARAM_PARTNERSHIP_FIELD_PROTOCOL = "partnerfield_protocol";
	public static final String PARAM_PARTNERSHIP_FIELD_CONTENT_TRANSFER_ENCODING = "partnerfield_content_transfer_encoding";
	public static final String PARAM_PARTNERSHIP_FIELD_AS2_URL = "partnerfield_as2_url";
	public static final String PARAM_PARTNERSHIP_FIELD_COMPRESSION_TYPE = "partnerfield_compression_type";
	public static final String PARAM_PARTNERSHIP_FIELD_SUBJECT = "partnerfield_subject";
	public static final String PARAM_PARTNERSHIP_FIELD_MDNSUBJECT = "partnerfield_mdnsubject";
	public static final String PARAM_PARTNERSHIP_FIELD_RESEND_MAX_RETRIES = "partnerfield_resend_max_retries";
	public static final String PARAM_PARTNERSHIP_FIELD_AS2_MDN_OPTION = "partnerfield_as2_mdn_option";
	public static final String PARAM_PARTNERSHIP_FIELD_AS2_MDN_TO = "partnerfield_as2_mdn_to";
	public static final String PARAM_PARTNERSHIP_FIELD_AS2_RECEIPT_OPTION = "partnerfield_as2_receipt_option";
	public static final String PARAM_PARTNERSHIP_FIELD_PREVENT_CANONICALIZATION_FOR_MIC = "partnerfield_prevent_canonicalization_for_mic";
	public static final String PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_SIGNING = "partnerfield_no_set_transfer_encoding_for_signing";
	public static final String PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_ENCRYPTION = "partnerfield_no_set_transfer_encoding_for_encryption";
	public static final String PARAM_PARTNERSHIP_FIELD_RENAME_DIGEST_TO_OLD_NAME = "partnerfield_rename_digest_to_old_name";
	public static final String PARAM_PARTNERSHIP_FIELD_REMOVE_CMS_ALGORITHM_PROTECTION_ATTRIB = "partnerfield_remove_cms_algorithm_protection_attrib";
	
	private FileMonitor fileMonitor;

	public void setFileMonitor(FileMonitor fileMonitor) {
		this.fileMonitor = fileMonitor;
	}

	public FileMonitor getFileMonitor() throws InvalidParameterException {
		boolean createMonitor = ((fileMonitor == null) && (getParameter(PARAM_INTERVAL, false) != null));
//
//        if (!createMonitor && (fileMonitor != null)) {
//            String filename = fileMonitor.getFilename();
//            createMonitor = ((filename != null) && !filename.equals(getFilename()));
//        }
//
//        if (createMonitor) {
//            if (fileMonitor != null) {
//                fileMonitor.stop();
//            }
//
//            int interval = getParameterInt(PARAM_INTERVAL, true);
//            File file = new File(getFilename());
//            fileMonitor = new FileMonitor(file, interval);
//            //fileMonitor.addListener(this);
//        }

		return fileMonitor;
	}

	public String getTablePartner() throws InvalidParameterException {
		return getParameter(PARAM_TABLE_PARTNER, "as2_partner");
	}

	public String getTablePartnership() throws InvalidParameterException {
		return getParameter(PARAM_TABLE_PARTNERSHIP, "as2_partnership");
	}

	public String getPaternFieldName() throws InvalidParameterException {
		return getParameter(PARAM_PARTNER_FIELD_NAME, "name");
	}

	public String getPaternFieldAs2Id() throws InvalidParameterException {
		return getParameter(PARAM_PARTNER_FIELD_AS2ID, AS2Partnership.PID_AS2);
	}

	public String getPaternFieldEmail() throws InvalidParameterException {
		return getParameter(PARAM_PARTNER_FIELD_EMAIL, Partnership.PID_EMAIL);
	}

	public String getPaternFieldX509Alias() throws InvalidParameterException {
		return getParameter(PARAM_PARTNER_FIELD_X509ALIAS, SecurePartnership.PID_X509_ALIAS);
	}

	public String getPaternshipFieldName() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_NAME, "name");
	}

	public String getPaternshipFieldSender() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_SENDER, Partnership.PTYPE_SENDER);
	}

	public String getPaternshipFieldReceiver() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_RECEIVER, Partnership.PTYPE_RECEIVER);
	}

	public String getPaternshipFieldSign() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_SIGN, SecurePartnership.PA_SIGN);
	}

	public String getPaternshipFieldEncrypt() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_ENCRYPT, SecurePartnership.PA_ENCRYPT);
	}

	public String getPaternshipFieldProtocol() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_PROTOCOL, Partnership.PA_PROTOCOL);
	}

	public String getPaternshipContentTransferEncoding() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_CONTENT_TRANSFER_ENCODING, Partnership.PA_CONTENT_TRANSFER_ENCODING);
	}

	public String getPaternshipFieldAs2Url() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_AS2_URL, AS2Partnership.PA_AS2_URL);
	}

	public String getPaternshipFieldCompressionType() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_COMPRESSION_TYPE, SecurePartnership.PA_COMPRESSION_TYPE);
	}

	public String getPaternshipFieldSubject() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_SUBJECT, Partnership.PA_SUBJECT);
	}

	public String getPaternshipFieldMDNSubject() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_MDNSUBJECT, ASXPartnership.PA_MDN_SUBJECT);
	}

	public String getPaternshipFieldResendMaxRetries() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_RESEND_MAX_RETRIES, AS2Partnership.PA_RESEND_MAX_RETRIES);
	}
	
	public String getPaternshipFieldPrecentCanonicalizationForMic() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_PREVENT_CANONICALIZATION_FOR_MIC, AS2Partnership.PA_PREVENT_CANONICALIZATION_FOR_MIC);
	}

	public String getPaternshipFieldNoSetTransfertEncodingForSigning() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_SIGNING, Partnership.PA_NO_SET_TRANSFER_ENCODING_FOR_SIGNING);
	}

	public String getPaternshipFieldNoSetTransfertEncodingForEncryption() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_ENCRYPTION, Partnership.PA_NO_SET_TRANSFER_ENCODING_FOR_ENCRYPTION);
	}

	public String getPaternshipFieldRenameDigestToOldName() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_RENAME_DIGEST_TO_OLD_NAME, Partnership.PA_RENAME_DIGEST_TO_OLD_NAME);
	}

	public String getPaternshipFieldRemoveProtectionAttrib() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_REMOVE_CMS_ALGORITHM_PROTECTION_ATTRIB, Partnership.PA_REMOVE_PROTECTION_ATTRIB);
	}

	public String getPaternshipFieldAs2MdnOption() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_AS2_MDN_OPTION, AS2Partnership.PA_AS2_MDN_OPTIONS);
	}

	public String getPaternshipFieldAs2MdnTo() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_AS2_MDN_TO, AS2Partnership.PA_AS2_MDN_TO);
	}

	public String getPaternshipFieldAs2ReceiptOption() throws InvalidParameterException {
		return getParameter(PARAM_PARTNERSHIP_FIELD_AS2_RECEIPT_OPTION, AS2Partnership.PA_AS2_RECEIPT_OPTION);
	}

	public void handle(FileMonitor monitor, File file, int eventID) {
		switch (eventID) {
			case FileMonitorListener.EVENT_MODIFIED:

				try {
					refresh();
					LOGGER.debug("- Partnerships Reloaded -");
				} catch (OpenAS2Exception oae) {
					oae.terminate();
				}

				break;
		}
	}

	@Override
	public void init(Session session, Map parameters) throws OpenAS2Exception {
		super.init(session, parameters);
		refresh();
	}

	@Override
	public void refresh() throws OpenAS2Exception {
		try {
			load();
			//getFileMonitor();
		} catch (Exception e) {
			throw new WrappedException(e);
		}
	}

	protected void load()
		throws IOException, OpenAS2Exception, SQLException {
		Map newPartners = new HashMap();
		List newPartnerships = new ArrayList();

		DBFactory dBFactory = DBFactory.getdbBFactory(getParameter(XMLSession.EL_DATABASECONFIG, null));

		Connection connexion = null;
		try {
			connexion = dBFactory.getConnection();
			Statement statement = connexion.createStatement();
			LOGGER.debug("Table: " + getTablePartner());
			try{
				ResultSet resultat = statement.executeQuery( "SELECT " + getPaternFieldName() + ", " + getPaternFieldAs2Id() + ", " + getPaternFieldEmail() + ", " + getPaternFieldX509Alias() + " FROM " + getTablePartner() + ";" );
				while (resultat.next()) {
					Properties properties = new Properties();
					properties.put(AS2Partnership.PID_AS2, resultat.getString(getPaternFieldAs2Id()));
					String email = resultat.getString(getPaternFieldEmail());
					if(email != null)
						properties.put(Partnership.PID_EMAIL, email);
					properties.put(SecurePartnership.PID_X509_ALIAS, resultat.getString(getPaternFieldX509Alias()));
					newPartners.put(resultat.getString(getPaternFieldName()), properties);
				}
			} catch ( SQLException e ) {
				LOGGER.error("Error in module " + getClass().getName());
				LOGGER.error(e.getMessage());
				StringBuilder builder = new StringBuilder("\n------ CREATE TABLE ------").append("\n");
				builder.append("CREATE TABLE `").append(getTablePartner()).append("` (").append("\n");
				builder.append("  `").append(getPaternFieldName()).append("` VARCHAR(60) NOT NULL,").append("\n");
				builder.append("  `").append(getPaternFieldAs2Id()).append("` VARCHAR(128) NOT NULL "
						+ "COMMENT 'The AS2 ID must be comprised of 1 to 128 printable ASCII characters and is case-sensitive',").append("\n").append("\n");
				builder.append("  `").append(getPaternFieldX509Alias()).append("` VARCHAR(60) NOT NULL,").append("\n");
				builder.append("  `").append(getPaternFieldEmail()).append("` VARCHAR(255) NULL,").append("\n");
				builder.append("  PRIMARY KEY (`").append(getPaternFieldName()).append("`))").append("\n");
				builder.append("  DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;").append("\n");
				builder.append("").append("\n");
				builder.append("Partner attribute options for module ").append(getClass().getName()).append(":").append("\n");
				builder.append("   - " + PARAM_PARTNER_FIELD_NAME).append("\n");
				builder.append("   - " + PARAM_PARTNER_FIELD_AS2ID).append("\n");
				builder.append("   - " + PARAM_PARTNER_FIELD_X509ALIAS).append("\n");
				builder.append("   - " + PARAM_PARTNER_FIELD_EMAIL).append("\n");
				builder.append("------------").append("\n");
				LOGGER.info(builder);
				throw e;
			}
			try {
				ResultSet resultat = statement.executeQuery("SELECT "
						+ getPaternshipFieldName() + ", "
						+ getPaternshipFieldSender() + ", "
						+ getPaternshipFieldReceiver() + ", "
						+ getPaternshipFieldProtocol() + ", "
						+ getPaternshipContentTransferEncoding() + ", "
						+ getPaternshipFieldCompressionType() + ", "
						+ getPaternshipFieldSubject() + ", "
						+ getPaternshipFieldMDNSubject() + ", "
						+ getPaternshipFieldAs2Url() + ", "
						+ getPaternshipFieldAs2MdnTo() + ", "
						+ getPaternshipFieldAs2ReceiptOption()+ ", "
						+ getPaternshipFieldAs2MdnOption() + ", "
						+ getPaternshipFieldEncrypt() + ", "
						+ getPaternshipFieldSign() + ", "
						+ getPaternshipFieldResendMaxRetries() + ", "
						+ getPaternshipFieldPrecentCanonicalizationForMic() + ", "
						+ getPaternshipFieldNoSetTransfertEncodingForSigning() + ", "
						+ getPaternshipFieldNoSetTransfertEncodingForEncryption() + ", "
						+ getPaternshipFieldRenameDigestToOldName() + ", "
						+ getPaternshipFieldRemoveProtectionAttrib()
						+ " FROM " + getTablePartnership()+ ";" );
				while (resultat.next()) {
					Partnership partnership = new Partnership();
					String sender = resultat.getString(getPaternshipFieldSender());
					String receiver = resultat.getString(getPaternshipFieldReceiver());
					partnership.setName(resultat.getString(getPaternshipFieldName()));
					partnership.setSenderIDs((Map) newPartners.get(sender));
					partnership.setSenderID("name", sender);
					partnership.setReceiverIDs((Map) newPartners.get(receiver));
					partnership.setReceiverID("name", receiver);

					Map<String, String> properties = new HashMap();
					properties.put(Partnership.PA_PROTOCOL, resultat.getString(getPaternshipFieldProtocol()));
					properties.put(Partnership.PA_CONTENT_TRANSFER_ENCODING, resultat.getString(getPaternshipContentTransferEncoding()));
					properties.put(SecurePartnership.PA_COMPRESSION_TYPE, resultat.getString(getPaternshipFieldCompressionType()));
					properties.put(Partnership.PA_SUBJECT, resultat.getString(getPaternshipFieldSubject()));
					properties.put(ASXPartnership.PA_MDN_SUBJECT, resultat.getString(getPaternshipFieldMDNSubject()));
					properties.put(AS2Partnership.PA_AS2_URL, resultat.getString(getPaternshipFieldAs2Url()));
					properties.put(AS2Partnership.PA_AS2_MDN_TO, resultat.getString(getPaternshipFieldAs2MdnTo()));
					properties.put(AS2Partnership.PA_AS2_RECEIPT_OPTION, resultat.getString(getPaternshipFieldAs2ReceiptOption()));
					properties.put(AS2Partnership.PA_AS2_MDN_OPTIONS, resultat.getString(getPaternshipFieldAs2MdnOption()));
					properties.put(SecurePartnership.PA_ENCRYPT, resultat.getString(getPaternshipFieldEncrypt()));
					properties.put(SecurePartnership.PA_SIGN, resultat.getString(getPaternshipFieldSign()));
					properties.put(AS2Partnership.PA_RESEND_MAX_RETRIES, resultat.getString(getPaternshipFieldResendMaxRetries()));
					properties.put(AS2Partnership.PA_PREVENT_CANONICALIZATION_FOR_MIC, resultat.getBoolean(getPaternshipFieldPrecentCanonicalizationForMic()) + "");
					properties.put(Partnership.PA_NO_SET_TRANSFER_ENCODING_FOR_SIGNING, resultat.getBoolean(getPaternshipFieldNoSetTransfertEncodingForSigning()) + "");
					properties.put(Partnership.PA_NO_SET_TRANSFER_ENCODING_FOR_ENCRYPTION, resultat.getBoolean(getPaternshipFieldNoSetTransfertEncodingForEncryption()) + "");
					properties.put(Partnership.PA_RENAME_DIGEST_TO_OLD_NAME, resultat.getBoolean(getPaternshipFieldRenameDigestToOldName()) + "");
					properties.put(Partnership.PA_REMOVE_PROTECTION_ATTRIB, resultat.getBoolean(getPaternshipFieldRemoveProtectionAttrib()) + "");

					partnership.setAttributes(properties);

					newPartnerships.add(partnership);
				}
			} catch (SQLException e) {
				LOGGER.error("Error in module " + getClass().getName());
				LOGGER.error(e.getMessage());
				StringBuilder builder = new StringBuilder("\n------ CREATE TABLE ------").append("\n");
				builder.append("CREATE TABLE `").append(getTablePartnership()).append("` (").append("\n");
				builder.append("  `").append(getPaternshipFieldName()).append("` VARCHAR(255),").append("\n");
				builder.append("  `").append(getPaternshipFieldSender()).append("` VARCHAR(60) NOT NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldReceiver()).append("` VARCHAR(60) NOT NULL,").append("\n");

				builder.append("  `").append(getPaternshipFieldProtocol()).append("` ENUM('" + IPartner.ATTRIBUTE_AS1ID + "', '" + IPartner.ATTRIBUTE_AS2ID + "') NOT NULL  DEFAULT '" + IPartner.ATTRIBUTE_AS2ID + "',").append("\n");
				
				builder.append("  `").append(getPaternshipContentTransferEncoding()).append("` VARCHAR(30) NOT NULL DEFAULT '").append(ICryptoHelper.CONTENT_TRANSFER_ENCODING_8BIT).append("',").append("\n");
				
				builder.append("  `").append(getPaternshipFieldCompressionType()).append("` ENUM('" + ICryptoHelper.COMPRESSION_ZLIB + "') DEFAULT NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldSubject()).append("` VARCHAR(255) NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldMDNSubject()).append("` VARCHAR(255) NULL,").append("\n");
				
				
				builder.append("  `").append(getPaternshipFieldAs2Url()).append("` VARCHAR(255) NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldAs2MdnTo()).append("` VARCHAR(255) NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldAs2ReceiptOption()).append("` VARCHAR(255) NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldAs2MdnOption()).append("` VARCHAR(255) NULL,").append("\n");
				
				builder.append("  `").append(getPaternshipFieldEncrypt()).append("` ENUM("
						+ "'" + ICryptoHelper.CRYPT_3DES + "'"
						+ ", '" + ICryptoHelper.CRYPT_CAST5 + "'"
						+ ", '" + ICryptoHelper.CRYPT_IDEA + "'"
						+ ", '" + ICryptoHelper.CRYPT_RC2 + "'"
						+ ", '" + ICryptoHelper.CRYPT_RC2_CBC + "'"
						+ ", '" + ICryptoHelper.AES128_CBC + "'"
						+ ", '" + ICryptoHelper.AES192_CBC + "'"
						+ ", '" + ICryptoHelper.AES256_CBC + "'"
						+ ", '" + ICryptoHelper.AES256_WRAP + "'"
						+ ") NOT NULL  DEFAULT '" + ICryptoHelper.CRYPT_3DES + "',").append("\n");
				builder.append("  `").append(getPaternshipFieldSign()).append("` ENUM("
						+ " '" + ICryptoHelper.DIGEST_MD2 + "'"
						+ ", '" + ICryptoHelper.DIGEST_MD5 + "'"
						+ ", '" + ICryptoHelper.DIGEST_SHA1 + "'"
						+ ", '" + ICryptoHelper.DIGEST_SHA224 + "'"
						+ ", '" + ICryptoHelper.DIGEST_SHA256 + "'"
						+ ", '" + ICryptoHelper.DIGEST_SHA384 + "'"
						+ ", '" + ICryptoHelper.DIGEST_SHA512 + "'"
						+ ") NOT NULL  DEFAULT '" + ICryptoHelper.DIGEST_SHA256 + "',").append("\n");


				builder.append("  `").append(getPaternshipFieldResendMaxRetries()).append("` SMALLINT UNSIGNED NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldPrecentCanonicalizationForMic()).append("` BOOLEAN NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldNoSetTransfertEncodingForSigning()).append("` BOOLEAN NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldNoSetTransfertEncodingForEncryption()).append("` BOOLEAN NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldRenameDigestToOldName()).append("` BOOLEAN NULL,").append("\n");
				builder.append("  `").append(getPaternshipFieldRemoveProtectionAttrib()).append("` BOOLEAN NULL,").append("\n");
				builder.append("  PRIMARY KEY (`").append(getPaternshipFieldName()).append("`),").append("\n");
				builder.append("  UNIQUE INDEX `unique_").append(getPaternshipFieldSender()).append("_").append(getPaternshipFieldReceiver()).append("` (`").append(getPaternshipFieldSender()).append("` ASC, `").append(getPaternshipFieldReceiver()).append("` ASC),").append("\n");
				builder.append("  FOREIGN KEY (`").append(getPaternshipFieldSender()).append("`) REFERENCES `").append(getTablePartner()).append("` (`").append(getPaternFieldName()).append("`) ON DELETE CASCADE ON UPDATE CASCADE,").append("\n");
				builder.append("  FOREIGN KEY (`").append(getPaternshipFieldReceiver()).append("`) REFERENCES `").append(getTablePartner()).append("` (`").append(getPaternFieldName()).append("`) ON DELETE CASCADE ON UPDATE CASCADE)").append("\n");
				builder.append("  DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;").append("\n");
				builder.append("").append("\n");
				builder.append("Partnership attribute options for module ").append(getClass().getName()).append(":").append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_SENDER).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_RECEIVER).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_PROTOCOL).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_CONTENT_TRANSFER_ENCODING).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_COMPRESSION_TYPE).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_SUBJECT).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_MDNSUBJECT).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_AS2_URL).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_AS2_MDN_TO).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_AS2_RECEIPT_OPTION).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_AS2_MDN_OPTION).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_ENCRYPT).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_SIGN).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_RESEND_MAX_RETRIES).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_PREVENT_CANONICALIZATION_FOR_MIC).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_SIGNING).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_NO_SET_TRANSFER_ENCODING_FOR_ENCRYPTION).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_RENAME_DIGEST_TO_OLD_NAME).append("\n");
				builder.append("   - " + PARAM_PARTNERSHIP_FIELD_REMOVE_CMS_ALGORITHM_PROTECTION_ATTRIB).append("\n");
				builder.append("------------").append("\n");
				LOGGER.info(builder);
				throw e;
			}
			try{
				ResultSet resultat = statement.executeQuery( "SELECT 1 FROM `" + dBFactory.getTableMessage() + "`;" );
				while (resultat.next()) {
				}
			} catch ( SQLException e ) {
				LOGGER.error("Error in module " + getClass().getName());
				LOGGER.error(e.getMessage());
				StringBuilder builder = new StringBuilder("\n------ CREATE TABLE ------").append("\n");
				builder.append("CREATE TABLE `").append(dBFactory.getTableMessage()).append("` (").append("\n")
				.append("`id` int(11) NOT NULL AUTO_INCREMENT,\n")
				.append("`partnership` varchar(255) NOT NULL,\n")
				.append("`message_id` varchar(255) NOT NULL,\n")
				.append("`filename` varchar(255) DEFAULT NULL,\n")
				.append("`date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
				.append("`mdn_id` varchar(255) DEFAULT NULL,\n")
				.append("`mdn_filename` varchar(255) DEFAULT NULL,\n")
				.append("`mdn_date` timestamp NULL DEFAULT NULL,\n")
				.append("`status` varchar(20) DEFAULT NULL,\n")
				.append("`comment` varchar(2000) DEFAULT NULL,\n")
				.append("PRIMARY KEY (`id`),\n")
				.append("KEY `fk_partnership_").append(dBFactory.getTableMessage()).append("` (`partnership`),\n")
				.append("KEY `date_").append(dBFactory.getTableMessage()).append("` (`date`),\n")
				.append("KEY `status_").append(dBFactory.getTableMessage()).append("` (`status`),\n")
				.append("KEY `messageId_").append(dBFactory.getTableMessage()).append("` (`message_id`),\n")
				.append("CONSTRAINT `fk_partnership_").append(dBFactory.getTableMessage()).append("` FOREIGN KEY (`partnership`) REFERENCES `as2_partnership` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,\n")
				.append("CONSTRAINT `fk_status_").append(dBFactory.getTableMessage()).append("` FOREIGN KEY (`status`) REFERENCES `as2_message_status` (`status`) ON UPDATE CASCADE\n")
				.append(") DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;\n");
				builder.append("------------").append("\n");
				LOGGER.info(builder);
				throw e;
			}
		} catch(SQLException e) {
			LOGGER.error("Error in module " + getClass().getName());
			throw e;
			
		} finally {
//			if ( connexion != null )
//				try {
//					/* Fermeture de la connexion */
//					connexion.close();
//				} catch ( SQLException ignore ) {
//					/* Si une erreur survient lors de la fermeture, il suffit de l'ignorer. */
//				}
		}
		synchronized (this) {
			setPartners(newPartners);
			setPartnerships(newPartnerships);
		}
	}

	protected void loadAttributes(Node node, Partnership partnership)
		throws OpenAS2Exception {
		Map nodes = XMLUtil.mapAttributeNodes(node.getChildNodes(), "attribute", "name", "value");

		partnership.getAttributes().putAll(nodes);
	}

	public void loadPartner(Map partners, Node node)
		throws OpenAS2Exception {
		String[] requiredAttributes = {"name"};

		Map newPartner = XMLUtil.mapAttributes(node, requiredAttributes);
		String name = (String) newPartner.get("name");

		if (partners.get(name) != null) {
			throw new OpenAS2Exception("Partner is defined more than once: " + name);
		}

		partners.put(name, newPartner);
	}



	protected void loadPartnerIDs(Map partners, String partnershipName, Node partnershipNode,
		String partnerType, Map idMap) throws OpenAS2Exception {
		Node partnerNode = XMLUtil.findChildNode(partnershipNode, partnerType);

		if (partnerNode == null) {
			throw new OpenAS2Exception("Partnership " + partnershipName + " is missing sender");
		}

		Map partnerAttr = XMLUtil.mapAttributes(partnerNode);

		// check for a partner name, and look up in partners list if one is found
		String partnerName = (String) partnerAttr.get("name");

		if (partnerName != null) {
			Map partner = (Map) partners.get(partnerName);

			if (partner == null) {
				throw new OpenAS2Exception("Partnership " + partnershipName + " has an undefined " +
					partnerType + ": " + partnerName);
			}

			idMap.putAll(partner);
		}

		// copy all other attributes to the partner id map		
		idMap.putAll(partnerAttr);
	}

	public void loadPartnership(Map partners, List partnerships, Node node)
		throws OpenAS2Exception {
		Partnership partnership = new Partnership();
		String[] requiredAttributes = {"name"};

		Map psAttributes = XMLUtil.mapAttributes(node, requiredAttributes);
		String name = (String) psAttributes.get("name");

		if (getPartnership(partnerships, name) != null) {
			throw new OpenAS2Exception("Partnership is defined more than once: " + name);
		}

		partnership.setName(name);

		// load the sender and receiver information
		loadPartnerIDs(partners, name, node, Partnership.PTYPE_SENDER, partnership.getSenderIDs());
		loadPartnerIDs(partners, name, node, Partnership.PTYPE_RECEIVER, partnership.getReceiverIDs());

		// read in the partnership attributes
		loadAttributes(node, partnership);

		// add the partnership to the list of available partnerships
		partnerships.add(partnership);
	}

	public void storePartnership()
	throws OpenAS2Exception {
		String fn = "host";


		DecimalFormat df = new DecimalFormat("0000000");
		long l = 0;
		File f = null;
		while (true) {
			f = new File(fn+'.'+df.format(l));
			if (f.exists() == false)
				break;
			l++;
		}

		LOGGER.info("backing up "+ fn +" to "+ f.getName());

		File fr = new File(fn);
		fr.renameTo(f);

		try {
			PrintWriter pw = new PrintWriter(new FileOutputStream(fn));
			pw.println("<partnerships>");
			Iterator partnerIt = partners.entrySet().iterator();
			while (partnerIt.hasNext()) {
				Map.Entry ptrnData = (Map.Entry) partnerIt.next();
				HashMap partnerMap = (HashMap) ptrnData.getValue();
				pw.print("  <partner ");
				Iterator attrIt = partnerMap.entrySet().iterator();
				while (attrIt.hasNext()) {
					Map.Entry attribute = (Map.Entry) attrIt.next();
					pw.print(attribute.getKey()+"=\""+attribute.getValue()+"\"");
					if (attrIt.hasNext())
						pw.print("\n           ");
				}
				pw.println("/>");
		}
		List partnerShips = getPartnerships();
		ListIterator partnerLIt = (ListIterator) partnerShips.listIterator();
		while (partnerLIt.hasNext()) {
			Partnership partnership = (Partnership) partnerLIt.next();
			pw.println("  <partnership name=\""+partnership.getName()+"\">");
			pw.println("    <sender name=\""+ partnership.getSenderIDs().get("name")+"\"/>");
			pw.println("    <receiver name=\""+ partnership.getReceiverIDs().get("name")+"\"/>");
			Map partnershipMap = partnership.getAttributes();

			Iterator partnershipIt = partnershipMap.entrySet().iterator();
			while (partnershipIt.hasNext()) {
				Map.Entry partnershipData = (Map.Entry) partnershipIt.next();
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