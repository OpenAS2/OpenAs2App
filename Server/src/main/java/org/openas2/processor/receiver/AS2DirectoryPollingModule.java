package org.openas2.processor.receiver;

import java.util.HashMap;

import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;

public class AS2DirectoryPollingModule extends DirectoryPollingModule {

	protected AS2Message createMessage() {
		return new AS2Message();
	}

}
