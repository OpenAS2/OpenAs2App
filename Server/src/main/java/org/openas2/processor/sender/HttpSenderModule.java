package org.openas2.processor.sender;

import java.net.HttpURLConnection;

import org.openas2.OpenAS2Exception;
import org.openas2.util.HTTPUtil;


public abstract class HttpSenderModule extends BaseSenderModule implements SenderModule {
	
	  public static final String PARAM_READ_TIMEOUT = "readtimeout";
	  public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
	  
  	//private Log logger = LogFactory.getLog(HttpSenderModule.class.getSimpleName());
	
    public HttpURLConnection getConnection(String url, boolean output, boolean input,
        boolean useCaches, String requestMethod) throws OpenAS2Exception
    {
    	if (url == null) throw new OpenAS2Exception("HTTP sender module received empty URL string.");
        System.setProperty("sun.net.client.defaultReadTimeout", getParameter(PARAM_READ_TIMEOUT, "60000"));
        System.setProperty("sun.net.client.defaultConnectTimeout", getParameter(PARAM_CONNECT_TIMEOUT, "60000"));
        return HTTPUtil.getConnection(url, output, input, useCaches, requestMethod);
    }
}
