package org.openas2.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.openas2.OpenAS2Exception;
import org.openas2.message.InvalidMessageException;


public class IOUtilOld {
    public static final String VALID_FILENAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.@-";

    public static File getDirectoryFile(String directory)
        throws IOException {
        File directoryFile = new File(directory);

        if (!directoryFile.exists()) {
            if (!directoryFile.mkdirs()) {
                throw new IOException("Could not create directory: " + directory);
            }
        }

        if (!directoryFile.isDirectory()) {
            throw new IOException("Invalid directory: " + directory);
        }

        return directoryFile;
    }

  public static byte[] getFileBytes(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int byteCount;

        while ((byteCount = fis.read(buf)) >= 0) {
            baos.write(buf, 0, byteCount);
        }

        fis.close();

        return baos.toByteArray();
    }

    public static String getTransferRate(int bytes, ProfilerStub stub) {
        StringBuffer buf = new StringBuffer();
        buf.append(bytes).append(" bytes in ");
        buf.append(stub.getCombined()).append(" at ");

        long time = stub.getDifference();

        if (time != 0) {
            double stime = time / 1000.0;
            long rate = Math.round(bytes / stime);
            buf.append(getTransferRate(rate));
        } else {
            buf.append(getTransferRate(bytes));
        }

        return buf.toString();
    }

    public static String getTransferRate(long bytesPerSecond) {
        StringBuffer buf = new StringBuffer();
        long kbytesPerSecond = bytesPerSecond / 1024;

        if (bytesPerSecond < 1024) {
            buf.append(bytesPerSecond).append(" Bps");
        } else if (kbytesPerSecond < 1024) {
            buf.append(kbytesPerSecond).append(".").append(bytesPerSecond % 1024).append(" KBps");
        } else {
            buf.append(kbytesPerSecond / 1024).append(".").append(kbytesPerSecond % 1024).append(" MBps");
        }

        return buf.toString();
    }

    public static File getUnique(File dir, String filename) {
        File test;
        int counter = -1;
        filename = cleanFilename(filename);

        while (true) {
            if (counter == -1) {
                test = new File(dir, filename);
            } else {
                test = new File(dir, filename + "." + Integer.toString(counter));
            }

            if (!test.exists()) {
                return test;
            }

            counter++;
        }
    }

    public static String cleanFilename(String filename) {
        byte[] fnBytes = filename.getBytes();
        byte c;

        for (int i = 0; i < fnBytes.length; i++) {
            c = fnBytes[i];

            if (VALID_FILENAME_CHARS.indexOf(c) == -1) {
                fnBytes[i] = '_';
            }
        }

        return new String(fnBytes);
    }

    public static int copy(InputStream in, OutputStream out)
        throws IOException {
        int totalCount = 0;
        byte[] buf = new byte[4096];
        int count = 0;

        while (count >= 0) {
            count = in.read(buf);
            totalCount += count;

            if (count > 0) {
                out.write(buf, 0, count);
            }
        }

        return totalCount;
    }

    public static int copy(InputStream in, OutputStream out, int contentSize)
        throws IOException {
        int totalCount = 0;
        byte[] buf = new byte[4096];
        int bytesIn = 0;

        /*Timer timer = new Timer(180); // 3 minute timeout

        while (bytesIn >= 0 && totalCount < contentSize) {
                if (in.available() > 0) {
                        bytesIn = in.read(buf);
                        totalCount += bytesIn;

                        if (bytesIn > 0) {
                                out.write(buf, 0, bytesIn);
                        }
                        timer.reset();
                }
                if (timer.isTimedOut()) {
                        throw new IOException("Copy time-out after " + Integer.toString(totalCount) + " bytes");
                }
        }*/
        while ((bytesIn >= 0) && (totalCount < contentSize)) {
            bytesIn = in.read(buf);
            totalCount += bytesIn;

            if (bytesIn > 0) {
                out.write(buf, 0, bytesIn);
            }
        }

        return totalCount;
    }

    public static void copyFile(File src, File dest) throws IOException {
        FileInputStream fIn = new FileInputStream(src);
        FileOutputStream fOut = new FileOutputStream(dest);

        try {
            copy(fIn, fOut);
        } finally {
            fIn.close();
            fOut.close();
        }
    }

    // move the file to an error directory    
    public static void handleError(File file, String errorDirectory)
        throws OpenAS2Exception {
        File destFile = null;

        try {
            File errorDir = IOUtilOld.getDirectoryFile(errorDirectory);

            destFile = new File(errorDir, file.getName());

            // move the file
            destFile = IOUtilOld.moveFile(file, destFile, false, true);
        } catch (IOException ioe) {
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

    public static void moveFile(File src, File dest) throws IOException {
        copyFile(src, dest);

        //if (!new File(file.getAbsolutePath()).delete()) {  // do this if file deletion always fails, may help
        if (!src.delete()) {
            dest.delete();
            throw new IOException("Move failed, unable to delete " + src);
        }
    }

    public static File moveFile(File src, File dest, boolean overwrite, boolean rename)
        throws IOException {
        if (!overwrite && dest.exists()) {
            if (rename) {
                dest = getUnique(dest.getAbsoluteFile().getParentFile(), dest.getName());
            } else {
                throw new IOException("File already exists: " + dest);
            }
        }

        copyFile(src, dest);

        //if (!new File(file.getAbsolutePath()).delete()) {  // do this if file deletion always fails, may help
        if (!src.delete()) {
            dest.delete();
            throw new IOException("Move failed, unable to delete " + src);
        }

        return dest;
    }

    public static byte[] toArray(InputStream in) throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int count = in.read(buf);

        while (count >= 0) {
            bOut.write(buf, 0, count);
            count = in.read(buf);
        }

        return bOut.toByteArray();
    }
}
