package org.openas2.test;

import org.apache.commons.logging.LogFactory;
import org.openas2.Session;
import org.openas2.XMLSession;
import org.openas2.cert.CertificateFactory;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.partner.Partnership;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author christopher broderick
 */
public class MimeBodyPartEncodingTest {
    protected static OutputStream sysOut;
    protected static BufferedWriter sysOutWriter;


    //todo move to tests
    public static void main(String[] args) {
        XMLSession session = null;
        int exitStatus = 0;
        System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
        //System.setProperty("openas2log.properties", "Server/bin/openas2log.properties");
        LogFactory.getFactory().setAttribute("level", "TRACE");
        System.out.println("Current working directory: " + System.getProperty("user.dir") + System.getProperty("line.separator"));
        System.out.println("Logging prop: " + System.getProperty("org.apache.commons.logging.Log") + System.getProperty("line.separator"));
        File f = new File(TestConfig.TEST_OUTPUT_FOLDER);
        f.mkdirs();

        try {
            write("Starting test..." + System.getProperty("line.separator"));

            // create the OpenAS2 Session object
            // this is used by all other objects to access global configs and
            // functionality
            write("Loading configuration..." + System.getProperty("line.separator"));
            String configFile = "Server/config/config.xml";
            if (args.length == 1) {
                configFile = args[0];
            } else if (args.length > 1) {
                write("Current working directory: " + System.getProperty("user.dir") + System.getProperty("line.separator"));
                write("Usage:" + System.getProperty("line.separator"));
                write("java org.openas2.app.OpenAS2Server <configuration file>" + System.getProperty("line.separator"));
                throw new Exception("Missing configuration file");
            }
            session = new XMLSession(configFile);
            // Do the deed...
            write("Entering test phase...." + System.getProperty("line.separator"));
            Message msg = new AS2Message();
            getPartnerInfo(msg);
            // update the message's partnership with any stored information
            session.getPartnershipFactory().updatePartnership(msg, true);
            Map<String, String> attribs = msg.getPartnership().getAttributes();
            write("Partnership attributes:\n" + msg.getPartnership().getName());
            for (String key : attribs.keySet()) {
                write("\t" + key + " ::= " + attribs.get(key) + System.getProperty("line.separator"));
            }

            msg.setAttribute(FileAttribute.MA_FILEPATH, TestConfig.TEST_SOURCE_FOLDER);
            msg.setAttribute(FileAttribute.MA_FILENAME, TestConfig.TEST_DEFAULT_SRC_FILE_NAME);
            byte[] data = TestConfig.DEFAULT_MESSAGE_TEXT.getBytes();
            String contentType = "application/octet-stream";
            ByteArrayDataSource byteSource = new ByteArrayDataSource(data, contentType, null);
            MimeBodyPart body = new MimeBodyPart();
            body.setDataHandler(new DataHandler(byteSource));

            // below statement is not filename related, just want to make it
            // consist with the parameter "mimetype="application/EDI-X12""
            // defined in config.xml 2007-06-01

            body.setHeader("Content-Type", contentType);

            // add below statement will tell the receiver to save the filename
            // as the one sent by sender. 2007-06-01
            String contentDisposition = "Attachment; filename=\"" + msg.getAttribute(FileAttribute.MA_FILENAME) + "\"";
            body.setHeader("Content-Disposition", contentDisposition);
            msg.setContentDisposition(contentDisposition);

            String contentTxfrEncoding = msg.getPartnership().getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
            if (contentTxfrEncoding == null) {
                contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
            }
            write("Using Content-Transfer-Encoding: " + contentTxfrEncoding + System.getProperty("line.separator"));
            body.addHeader("Content-Transfer-Encoding", contentTxfrEncoding);

            msg.setData(body);

            // update the message's partnership with any stored information
            session.getPartnershipFactory().updatePartnership(msg, true);
            msg.updateMessageID();

            CertificateFactory certFx = session.getCertificateFactory();

            X509Certificate senderCert = certFx.getCertificate(msg, Partnership.PTYPE_SENDER);

            PrivateKey senderKey = certFx.getPrivateKey(msg, senderCert);
            String digest = msg.getPartnership().getAttribute(Partnership.PA_SIGNATURE_ALGORITHM);

            System.out.println("Params for creating signed body part:: SIGN DIGEST: " + digest + "\n CERT ALG NAME EXTRACTED: " + senderCert.getSigAlgName() + "\n CERT PUB KEY ALG NAME EXTRACTED: " + senderCert.getPublicKey().getAlgorithm() + msg.getLogMsgID());

            String testFile = TestConfig.TEST_OUTPUT_FOLDER + "/" + TestConfig.TEST_DEFAULT_TGT_FILE_NAME + ".presigning";
            FileOutputStream fos = new FileOutputStream(testFile);
            write(fos, body);
            fos.close();
            System.out.println("MimeBodyPart written to: " + testFile);
            boolean isRemoveCmsAlgorithmProtectionAttr = "true".equalsIgnoreCase(msg.getPartnership().getAttribute(Partnership.PA_REMOVE_PROTECTION_ATTRIB));
            MimeBodyPart signedMbp = AS2Util.getCryptoHelper().sign(body, senderCert, senderKey, digest, contentTxfrEncoding, msg.getPartnership().isRenameDigestToOldName(), isRemoveCmsAlgorithmProtectionAttr);
            testFile = TestConfig.TEST_OUTPUT_FOLDER + "/" + TestConfig.TEST_DEFAULT_TGT_FILE_NAME + ".signed";
            fos = new FileOutputStream(testFile);
            write(fos, signedMbp);
            fos.close();
            System.out.println("MimeBodyPart written to: " + testFile);

            String algorithm = msg.getPartnership().getAttribute(Partnership.PA_ENCRYPTION_ALGORITHM);
            X509Certificate receiverCert = certFx.getCertificate(msg, Partnership.PTYPE_RECEIVER);
            signedMbp = AS2Util.getCryptoHelper().encrypt(signedMbp, receiverCert, algorithm, contentTxfrEncoding);
            testFile = TestConfig.TEST_OUTPUT_FOLDER + "/" + TestConfig.TEST_DEFAULT_TGT_FILE_NAME + ".encrypted";
            fos = new FileOutputStream(testFile);
            write(fos, signedMbp);
            fos.close();
            System.out.println("MimeBodyPart written to: " + testFile);


        } catch (Exception e) {
            exitStatus = -1;
            e.printStackTrace();
        } catch (Error err) {
            exitStatus = -1;
            err.printStackTrace();
        } finally {

            write("OpenAS2 test has shut down." + System.getProperty("line.separator"));

            System.exit(exitStatus);
        }
    }

    public static void write(OutputStream os, MimeBodyPart mbp) throws MessagingException, IOException {
        os.write((System.getProperty("line.separator") + "========BEGIN MIMEBODYPART=========" + System.getProperty("line.separator")).getBytes());
        mbp.writeTo(os);
        os.write((System.getProperty("line.separator") + "========END MIMEBODYPART=========" + System.getProperty("line.separator")).getBytes());
    }

    public static void write(String msg) {
        if (sysOutWriter == null) {
            sysOutWriter = new BufferedWriter(new OutputStreamWriter(System.out));
        }

        try {
            sysOutWriter.write(msg);
            sysOutWriter.flush();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public static void getPartnerInfo(Message msg) throws InvalidParameterException {
        MessageParameters params = new MessageParameters(msg);

        // Get the parameter that should provide the link between the polled
        // directory and an AS2 sender and recipient
        String defaults = System.getProperty("partnership.defaults", TestConfig.DEFAULT_PARTNER_INFO);
        // Link the file to an AS2 sender and recipient via the Message object
        // associated with the file
        params.setParameters(defaults);
    }
}
