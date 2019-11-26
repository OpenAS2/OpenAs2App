package org.openas2.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.OpenAS2Exception;
import org.openas2.util.AS2Util;
import org.openas2.util.Properties;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)

public class AttributeEnhancerTest {

    @Test
    public void enhancesAttributes() {
        Map<String, String> attribs = new HashMap<String, String>();
        attribs.put("key1", "ValueOfKey1");
        attribs.put("key2", "ValueOfKey2");
        attribs.put("key3", "Replaced with $attribute.key1$");
        attribs.put("key4", "Replaced with $attribute.key2$ and $attribute.key1$");
        attribs.put("key5", "Replaced with $properties.storageBaseDir$");
        Properties.setProperty("storageBaseDir", "%home%/../data");

        try {
            AS2Util.attributeEnhancer(attribs);
        } catch (OpenAS2Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        assertThat("Key 3 has been replaced", attribs.get("key3"), equalTo("Replaced with ValueOfKey1"));
        assertThat("Key 4 has been replaced", attribs.get("key4"), equalTo("Replaced with ValueOfKey2 and ValueOfKey1"));
        assertThat("Key 5 has been replaced", attribs.get("key5"), equalTo("Replaced with %home%/../data"));
    }
}
