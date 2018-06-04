package org.openas2.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.openas2.OpenAS2Exception;
import org.openas2.message.InvalidMessageException;


public class IOUtil {
    //private static final String VALID_FILENAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.@-";

    public static OutputStreamWriter getOutPutStreamWriter(String target, boolean createDirectories, String charsetName)
            throws IOException{
	if (charsetName == null) charsetName = StandardCharsets.UTF_8.name();
	Charset charSet = Charset.forName(charsetName);

        return new OutputStreamWriter(getFileOutPutStream(target, createDirectories), charSet);
    }

    public static FileOutputStream getFileOutPutStream(String target, boolean createDirectories)
            throws IOException {
        File tgtFile = new File(target);
	if (createDirectories) {
	    String directory = tgtFile.getParent();
	    File directoryFile = new File(directory);

	    if (!directoryFile.exists()) {
		if (!directoryFile.mkdirs()) {
		    throw new IOException("Could not create directory: " + directory);
		}
	    }

	    if (!directoryFile.isDirectory()) {
		throw new IOException("Invalid directory: " + directory);
	    }
	}

        return new FileOutputStream(tgtFile);
    }

    public static File getDirectoryFile(String directory)
            throws IOException
    {
        File directoryFile = new File(directory);

        if (!directoryFile.exists())
        {
            if (!directoryFile.mkdirs())
            {
                throw new IOException("Could not create directory: " + directory);
            }
        }

        if (!directoryFile.isDirectory())
        {
            throw new IOException("Invalid directory: " + directory);
        }

        return directoryFile;
    }

    public static String getTransferRate(int bytes, ProfilerStub stub)
    {
        StringBuffer buf = new StringBuffer();
        buf.append(bytes).append(" bytes in ");
        buf.append(stub.getCombined()).append(" at ");

        long time = stub.getDifference();

        if (time != 0)
        {
            double stime = time / 1000.0;
            long rate = Math.round(bytes / stime);
            buf.append(getTransferRate(rate));
        } else
        {
            buf.append(getTransferRate(bytes));
        }

        return buf.toString();
    }

    private static String getTransferRate(long bytesPerSecond)
    {
        StringBuffer buf = new StringBuffer();
        long kbytesPerSecond = bytesPerSecond / 1024;

        if (bytesPerSecond < 1024)
        {
            buf.append(bytesPerSecond).append(" Bps");
        } else if (kbytesPerSecond < 1024)
        {
            buf.append(kbytesPerSecond).append(".").append(bytesPerSecond % 1024).append(" KBps");
        } else
        {
            buf.append(kbytesPerSecond / 1024).append(".").append(kbytesPerSecond % 1024).append(" MBps");
        }

        return buf.toString();
    }

    public static File getUnique(File dir, String filename)
    {
        filename = cleanFilename(filename);
        return new File(dir, filename + "." + UUID.randomUUID());
    }

    public static String cleanFilename(String filename) {
	/*
	 * byte[] fnBytes = filename.getBytes(); byte c;
	 * 
	 * for (int i = 0; i < fnBytes.length; i++) { c = fnBytes[i];
	 * 
	 * if (VALID_FILENAME_CHARS.indexOf(c) == -1) { fnBytes[i] = '_'; } }
	 * 
	 * return new String(fnBytes);
	 */
	String reservedFilenameChars = Properties.getProperty("reservedFilenameCharacters", "<>:\"|?*");
	if (reservedFilenameChars != null && reservedFilenameChars.length() > 0) {
	    String srchReplStr = reservedFilenameChars.replaceAll("\\[", "\\[").replaceAll("\\]", "\\]");
	    if (reservedFilenameChars.contains(":") && filename.matches("^[a-zA-Z]{1}:.*")) {
		filename = filename.substring(0, 2) + filename.substring(2).replaceAll("[" + srchReplStr + "]", "");
	    } else
		filename = filename.replaceAll("[" + srchReplStr + "]", "");
	}
	return filename;

    }

    // move the file to an error directory    
    public static void handleError(File file, String errorDirectory)
            throws OpenAS2Exception
    {
        File destFile = null;

        try
        {
            File errorDir = IOUtil.getDirectoryFile(errorDirectory);

            destFile = new File(errorDir, file.getName());

            // move the file
            destFile = IOUtil.moveFile(file, destFile, false, true);
        } catch (IOException ioe)
        {
            InvalidMessageException im = new InvalidMessageException("Failed to move " +
                    file.getAbsolutePath() + " to error directory " + destFile.getAbsolutePath());
            im.initCause(ioe);
            throw im;
        }

        // make sure an error of this event is logged
        InvalidMessageException imMoved = new InvalidMessageException("Moved " +
                file.getAbsolutePath() + " to " + destFile.getAbsolutePath());
        imMoved.terminate();
    }


    public static File moveFile(File src, File dest, boolean overwrite, boolean rename)
            throws IOException
    {
        if (!overwrite && dest.exists())
        {
            if (rename)
            {
                dest = getUnique(dest.getAbsoluteFile().getParentFile(), dest.getName());
            } else
            {
                throw new IOException("File already exists: " + dest);
            }
        }

        //copyFile(src, dest); //todo why copy??
        FileUtils.copyFile(src, dest); //


        //if (!new File(file.getAbsolutePath()).delete()) {  // do this if file deletion always fails, may help
        if (!src.delete())
        {
            dest.delete();
            throw new IOException("Move failed, unable to delete " + src);
        }

        return dest;
    }

    public static void deleteFile(File f) throws IOException
    {
        if (!f.exists())
        {
            return;
        }
        // Delete the file using java.nio.file.Files.delete() instead of java.io.File.delete()
        // if possible (requires Java 7 or better) so exception can be used for debugging cause of fail
        boolean canUseNio = true;
        try
        {
            Class.forName("java.nio.file.Path");
            // it exists on the classpath
        } catch (ClassNotFoundException e)
        {
            // it does not exist on the classpath
            canUseNio = false;
        }
        if (canUseNio)
        {
            java.nio.file.Path fp = f.toPath();
            java.nio.file.Files.delete(fp);
        } else if (!f.delete())
        {
            throw new IOException("Failed to delete file: " + f.getName());
        }
    }

    public static File[] getFiles(File dir, final String extensionFilter)
    {
        return dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name)
            {
                return name.toLowerCase().endsWith("." + extensionFilter);
            }
        });
    }
}
