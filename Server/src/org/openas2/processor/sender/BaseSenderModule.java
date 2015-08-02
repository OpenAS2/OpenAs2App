package org.openas2.processor.sender;

import java.util.HashMap;
import java.util.Map;

import org.openas2.message.Message;
import org.openas2.OpenAS2Exception;
import org.openas2.processor.BaseProcessorModule;
import org.openas2.processor.resender.ResenderModule;


public abstract class BaseSenderModule extends BaseProcessorModule implements SenderModule {
	
	// How many times should this message be sent?
	protected int retries (Map<Object,Object> options) {
		String left;
		if (options == null || (left = (String) options.get(SenderModule.SOPT_RETRIES)) == null) {
			try {
				left = getParameter (SenderModule.SOPT_RETRIES, false);
			}
			catch (OpenAS2Exception e) {
				// Can't actualy happen, but try convincing Java of that.
				// *FIXME* should have two versions of getParameter, one that can't throw.
				left = null;
			}
			
			if (left == null) left = SenderModule.DEFAULT_RETRIES;
		}
		
		return Integer.parseInt(left);	
	}
	
    protected boolean resend(String how, Message msg, OpenAS2Exception cause, int tries) throws OpenAS2Exception {
    	if (tries >= 0 && tries -- <= 0) return false;
        Map<Object,Object> options = new HashMap<Object,Object>();
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, this);
        options.put(ResenderModule.OPTION_RESEND_METHOD, how);
        options.put(ResenderModule.OPTION_RETRIES, "" + tries);
        getSession().getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
        return true;
    }
}
