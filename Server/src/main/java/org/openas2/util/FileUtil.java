package org.openas2.util;

import org.openas2.OpenAS2Exception;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;


public class FileUtil {

    //private static final Log logger = LogFactory.getLog(FileUtil.class.getSimpleName());

    public static Properties loadProperties(String filename) throws IOException {
        Properties fileProps = new java.util.Properties();
        FileInputStream fis = null;
        fis = new FileInputStream(filename);
        try {
            fileProps.load(fis);
        } finally {
            if (fis != null) {
                    fis.close();
            }
        }
        return fileProps;
    }

    /** Attempts to extract the filename extension by searching for the last occurrence
     *  of a period and returning all characters following that period.
     *  If no period is found then it returns null.
     * @param filename - the full name of the file including extension
     * @return the extension of the filename excluding the period
     */
    public static String getFilenameExtension(String filename) {
        int period_index = filename.lastIndexOf(".");
        if (period_index == -1) {
            return null;
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public static void splitLineBasedFile(File sourceFile, String outputDir, long maxFileSize, boolean containsHeaderRow, String newFileBaseName, String filenamePrefix) throws OpenAS2Exception {
        FileReader fileReader;
        try {
            fileReader = new FileReader(sourceFile);
        } catch (FileNotFoundException e2) {
            throw new OpenAS2Exception("Source file for splitting not found: " + sourceFile.getAbsolutePath());
        }
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        try {
            byte[] headerRow = new byte[0];
            if (containsHeaderRow) {
                try {
                    String headerLine = bufferedReader.readLine();
                    if (headerLine != null) {
                        headerRow = (headerLine + "\n").getBytes();
                    }
                } catch (IOException e1) {
                    throw new OpenAS2Exception("Failed to read header row from input file: " + sourceFile.getAbsolutePath(), e1);
                }
            }
            long headerRowByteCount = headerRow.length;
            if (maxFileSize < headerRowByteCount) {
                // Would just write header repeatedly so throw error
                throw new OpenAS2Exception("Split file size is less than the header row size " + sourceFile.getAbsolutePath());
            }
            long expectedFileCnt = Math.floorDiv(sourceFile.length(), maxFileSize);
            // Figure out how many digits to pad the filename with - add 1 to cater for header row
            int fileCntDigits = Long.toString(expectedFileCnt).length() +1;
            int fileCount = 0;
            boolean notEof = true;
            byte[] lineBytes = null;
            while (notEof) {
                fileCount += 1;
                long fileSize = 0;
                String newFilename = filenamePrefix + StringUtil.padLeftZeros(Integer.toString(fileCount), fileCntDigits) + "-" + newFileBaseName;
                File outFile = new File(outputDir + File.separator + newFilename);
                BufferedOutputStream fos = null;
                try {
                    try {
                        fos = new BufferedOutputStream(new FileOutputStream(outFile));
                    } catch (IOException e) {
                        throw new OpenAS2Exception("Failed to initialise output file for file splitting on file " + fileCount, e);
                    }
                    if (containsHeaderRow) {
                        try {
                            fos.write(headerRow);
                        } catch (IOException e) {
                            throw new OpenAS2Exception("Failed to write header row to output file for file splitting on file " + fileCount, e);
                        }
                        fileSize += headerRowByteCount;
                    }
                    String line = null;
                    if (lineBytes == null) {
                        // Get the next line before going into the loop to ensure we can stay under the threshold
                        line = bufferedReader.readLine();
                        if (line == null) {
                            notEof = false;
                            break;
                        }
                        lineBytes = (line + "\n").getBytes();
                    }
                    fileSize += lineBytes.length;
                    while (fileSize < maxFileSize) {                      
                        fos.write(lineBytes);
                        fos.flush(); // Flush on each loop to keep memory usage low
                        line = bufferedReader.readLine();
                        if (line == null) {
                            notEof = false;
                            break;
                        }
                        lineBytes = (line + "\n").getBytes();
                        fileSize += lineBytes.length;
                    }
                    fos.flush();
                    fos.close();
                    if (line == null) {
                        notEof = false;
                    }
                } catch (IOException e) {
                    throw new OpenAS2Exception("Failed to write output file for file splitting on file " + fileCount, e);
                } finally {
                    try {
                        if (fos != null) {
                            fos.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        } finally {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                throw new OpenAS2Exception("Failed to close reader for input file.", e);
            }
        }        
    }

}