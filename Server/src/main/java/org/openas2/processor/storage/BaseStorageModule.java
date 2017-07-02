package org.openas2.processor.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.params.InvalidParameterException;
import org.openas2.processor.BaseProcessorModule;
import org.openas2.util.IOUtilOld;
import org.openas2.util.Properties;

public abstract class BaseStorageModule extends BaseProcessorModule implements StorageModule {
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_PROTOCOL = "protocol";
    public static final String PARAM_TEMPDIR = "tempdir";

    public boolean canHandle(String action, Message msg, Map<Object, Object> options)
    {
        try
        {
            if (!action.equals(getModuleAction()))
            {
                return false;
            }

            String modProtocol = getParameter(PARAM_PROTOCOL, false);
            String msgProtocol = msg.getProtocol();

            if (modProtocol != null)
            {
                if ((msgProtocol != null) && msgProtocol.equals(modProtocol))
                {
                    return true;
                }

                return false;
            }

            return true;
        } catch (OpenAS2Exception oae)
        {
            return false;
        }
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception
    {
        super.init(session, options);
        getParameter(PARAM_FILENAME, true);
    }

    protected abstract String getModuleAction();


    /**
     * Add one more method "getFile" to make no impact to all modules who call this method with
     * only two parameter "Message msg" & "String fileParam"
     */


    protected File getFile(Message msg, String fileParam) throws IOException,
            OpenAS2Exception
    {
        return getFile(msg, fileParam, "");
    }

    /**
     * @param msg
     * @param fileParam
     * @param action
     * @return
     * @throws IOException
     * @throws OpenAS2Exception
     * @since 2007-06-01
     */
    protected File getFile(Message msg, String fileParam, String action) throws IOException, OpenAS2Exception
    {
        String filename = getFilename(msg, fileParam, action);
        String reservedFilenameChars = Properties.getProperty("reservedFilenameCharacters", "<>:\"|?*");
        if (reservedFilenameChars != null && reservedFilenameChars.length() > 0)
        {
        	System.out.println("XXXXXXXXXXXXXXX         STRING: " + filename);
        	String srchReplStr = reservedFilenameChars.replaceAll("\\[", "\\[").replaceAll("\\]", "\\]");
           	System.out.println("XXXXXXXXXXXXXXX         MATCH: " + filename.matches("^[a-zA-Z]{1}:.*"));
           	if (reservedFilenameChars.contains(":") && filename.matches("^[a-zA-Z]{1}:.*"))
        	{
        		filename = filename.substring(0,  2) + filename.substring(2).replaceAll("["+srchReplStr + "]", "");
        	}
        	else filename = filename.replaceAll("[" + srchReplStr + "]", "");        	
        }
        

        // make sure the parent directories exist
        File file = new File(filename);
        File parentDir = file.getParentFile();
        parentDir.mkdirs();

        return file;

    }


    protected abstract String getFilename(Message msg, String fileParam, String action) throws InvalidParameterException;

    protected void store(File msgFile, InputStream in) throws IOException, OpenAS2Exception
    {
        String tempDirname = getParameter(PARAM_TEMPDIR, false);
        if (tempDirname != null)
        {
            // write the data to a temporary directory first
            File tempDir = IOUtilOld.getDirectoryFile(tempDirname);
            String tempFilename = msgFile.getName();
            File tempFile = IOUtilOld.getUnique(tempDir, tempFilename);
            writeStream(in, tempFile);

            // copy the temp file over to the destination
            IOUtilOld.moveFile(tempFile, msgFile, true, false);
        } else
        {
            writeStream(in, msgFile);
        }
    }

    protected void writeStream(InputStream in, File destination) throws IOException
    {
        FileOutputStream out = new FileOutputStream(destination);
        try
        {
            IOUtils.copy(in, out);
        } finally
        {
            IOUtils.closeQuietly(in, out);
        }
    }


}