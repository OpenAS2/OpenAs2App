package org.openas2.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.TestUtils;

/**
 * Verifies that storing the partnerships file does not keep adding whitespace to it.
 * <p>
 * The parser keeps the newlines and indentation between elements as text nodes and the serializer adds
 * indentation of its own, so a document stored without discarding the first lot was written with both.
 * Every save grew the file by another set of blank lines, without bound, and each save is also preceded
 * by a backup copy, so the growth was permanent. Any change through the console or the API stores the
 * file, so this compounded over the life of a deployment.
 */
public class PartnershipsFileFormattingTest {

    private File dir;
    private File partnershipsFile;
    private XMLPartnershipFactory factory;

    @BeforeEach
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("partnerships-formatting").toFile();
        partnershipsFile = new File(dir, "partnerships.xml");
        Files.copy(Paths.get("src", "config", "partnerships.xml"), partnershipsFile.toPath());
        factory = new XMLPartnershipFactory();
        factory.getParameters().put(XMLPartnershipFactory.PARAM_FILENAME, partnershipsFile.getAbsolutePath());
    }

    @Test
    public void repeatedSavesDoNotGrowTheFile() throws Exception {
        loadAndStore();
        String afterFirst = read();

        for (int i = 0; i < 4; i++) {
            loadAndStore();
        }

        assertEquals(afterFirst, read(),
                "storing the partnerships repeatedly must produce a byte identical file, not grow it");
    }

    @Test
    public void aFileThatAlreadyAccumulatedBlankLinesIsTidiedOnTheNextSave() throws Exception {
        // Damage it the way the bug did: extra whitespace between elements
        String damaged = read().replaceAll(">(\\s*)<", ">\n\n\n\n<");
        Files.write(partnershipsFile.toPath(), damaged.getBytes(StandardCharsets.UTF_8));
        long blankBefore = blankLinesOutsideComments(damaged);
        assertTrue(blankBefore > 100, "the fixture should be badly damaged, was " + blankBefore);

        loadAndStore();

        long blankAfter = blankLinesOutsideComments(read());
        assertTrue(blankAfter < 20,
                "the accumulated blank lines should be gone, went from " + blankBefore + " to " + blankAfter);
    }

    @Test
    public void savingDoesNotChangeAnyElementOrAttribute() throws Exception {
        // Compare the documents structurally rather than as text: every element with every attribute
        // and value must survive a save, because only layout whitespace may be discarded
        List<String> before = describe(parse(partnershipsFile));
        assertTrue(before.size() > 10, "the fixture should describe a real configuration, was " + before.size());

        loadAndStore();

        assertEquals(before, describe(parse(partnershipsFile)),
                "stripping layout whitespace must not add, remove or alter anything");
    }

    /** Flattens a document into one comparable line per element, with its attributes sorted. */
    private List<String> describe(Document doc) {
        List<String> out = new ArrayList<String>();
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            List<String> attribs = new ArrayList<String>();
            NamedNodeMap map = el.getAttributes();
            for (int j = 0; j < map.getLength(); j++) {
                attribs.add(map.item(j).getNodeName() + "=" + map.item(j).getNodeValue());
            }
            Collections.sort(attribs);
            out.add(el.getTagName() + " " + attribs);
        }
        return out;
    }

    private Document parse(File f) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
    }

    @Test
    public void theStoredFileIsStillReadableAndIndented() throws Exception {
        loadAndStore();
        String body = read();

        assertTrue(body.startsWith("<?xml"), "must still be a well formed XML document");
        assertTrue(body.contains("\n    <partner "), "the serializer should still indent the elements");
    }

    private Partnership partnershipByName(String name) {
        for (Partnership p : factory.getPartnerships()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        throw new IllegalStateException("fixture is missing partnership " + name);
    }

    private void loadAndStore() throws Exception {
        factory.loadPartnershipsFile();
        factory.storePartnership();
    }

    private String read() throws Exception {
        return new String(Files.readAllBytes(partnershipsFile.toPath()), StandardCharsets.UTF_8);
    }

    /** Blank lines inside an XML comment are part of the comment, not the formatting defect. */
    private long blankLinesOutsideComments(String body) {
        StringBuilder outside = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            int start = body.indexOf("<!--", i);
            if (start < 0) {
                outside.append(body, i, body.length());
                break;
            }
            outside.append(body, i, start);
            int end = body.indexOf("-->", start);
            i = end < 0 ? body.length() : end + 3;
        }
        return outside.toString().lines().filter(l -> l.trim().isEmpty()).count();
    }

    @org.junit.jupiter.api.AfterEach
    public void tearDown() {
        TestUtils.deleteDirectory(dir);
    }
}
