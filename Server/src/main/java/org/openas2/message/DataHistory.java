package org.openas2.message;

import javax.mail.internet.ContentType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class DataHistory implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private List<DataHistoryItem> items;

    public List<DataHistoryItem> getItems() {
        if (items == null) {
            items = new ArrayList<DataHistoryItem>();
        }

        return items;
    }

    boolean contains(ContentType type) {
        Iterator<DataHistoryItem> itemIt = getItems().iterator();

        while (itemIt.hasNext()) {
            DataHistoryItem item = itemIt.next();

            if ((item.getContentType() != null) && item.getContentType().match(type)) {
                return true;
            }
        }

        return false;
    }
}
