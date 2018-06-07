package org.openas2.util;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.lib.xml.PropertyReplacementFilter;
import org.w3c.dom.Document;
import org.xml.sax.helpers.XMLFilterImpl;

@RunWith(MockitoJUnitRunner.class)
public class XMLUtilTest {
	private static final String[][] TEST_ENVS = { { "ENV1", "SOMETHING" }, { "ENV2", "$ENV{ENV1}" } };

	private String[][] positiveTests = {
			{ "<tests><test>$ENV{" + TEST_ENVS[1][0] + "}</test></tests>",
					"<tests><test>" + TEST_ENVS[1][1] + "</test></tests>" },
			{ "<tests><test testEnvVal=\"$ENV{" + TEST_ENVS[0][0] + "}\"/></tests>",
					"<tests><test testEnvVal=\"" + TEST_ENVS[0][1] + "\"/></tests>" },
			{ "<tests><test testEnvVal=\"TEXT-BEFORE-$ENV{" + TEST_ENVS[0][0] + "}\"/></tests>",
					"<tests><test testEnvVal=\"TEXT-BEFORE-" + TEST_ENVS[0][1] + "\"/></tests>" },
			{ "<tests><test testEnvVal=\"$ENV{" + TEST_ENVS[0][0] + "}-SOME-AFTER\"/></tests>",
					"<tests><test testEnvVal=\"" + TEST_ENVS[0][1] + "-SOME-AFTER\"/></tests>" },
			{ "<tests><test testEnvVal=\"TEXT-BEFORE-$ENV{" + TEST_ENVS[0][0] + "}-SOME-AFTER\"/></tests>",
					"<tests><test testEnvVal=\"TEXT-BEFORE-" + TEST_ENVS[0][1] + "-SOME-AFTER\"/></tests>" },
			{ "<tests><test testEnvVal=\"$ENV{" + TEST_ENVS[0][0] + "}\">$ENV{" + TEST_ENVS[1][0] + "}</test></tests>",
						"<tests><test testEnvVal=\"" + TEST_ENVS[0][1] + "\">" + TEST_ENVS[1][1] + "</test></tests>" }
	};
	private String[][] negativeTests = { { "<tests><test testEnvVal=\"$ENV{NON_EXISTENT}\"/></tests>",
			"<tests><test testEnvVal=\"NON_EXISTENT\"/></tests>" } };

	@Before
	public void setUp() throws Exception {
		for (int i = 0; i < TEST_ENVS.length; i++) {
			System.setProperty(TEST_ENVS[i][0], TEST_ENVS[i][1]);
		}
	}

	@Test
	public void shouldParseProperty() throws Exception {
		XMLFilterImpl handler = new PropertyReplacementFilter();
		for (String[] strings : positiveTests) {
			InputStream in = new ByteArrayInputStream(strings[0].getBytes(StandardCharsets.UTF_8));
			Document doc = XMLUtil.parseXML(in, handler);
			String result = XMLUtil.domToString(doc);
			assertThat("Check " + strings[1] + " against " + result, result.matches(Pattern.quote(strings[1])),
					is(true));
		}
		for (String[] strings : negativeTests) {
			InputStream in = new ByteArrayInputStream(strings[0].getBytes(StandardCharsets.UTF_8));
			Document doc;
			String result = "";
			try {
				doc = XMLUtil.parseXML(in, handler);
				result = XMLUtil.domToString(doc);
			} catch (TransformerException e) {
				// Expect an exception since env var not available
				result = strings[1];
			}
			assertThat("Check " + strings[1] + " against " + result, result.matches(Pattern.quote(strings[1])),
					is(true));
		}
	}
}
