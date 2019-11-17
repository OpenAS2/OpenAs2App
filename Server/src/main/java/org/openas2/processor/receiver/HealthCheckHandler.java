package org.openas2.processor.receiver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.app.HealthCheck;
import org.openas2.util.HTTPUtil;

import javax.mail.internet.InternetHeaders;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class HealthCheckHandler implements NetModuleHandler {
    private HealthCheckModule module;

    private Log logger = LogFactory.getLog(HealthCheckHandler.class.getSimpleName());

    public HealthCheckHandler(HealthCheckModule module) {
        super();
        this.module = module;
    }

    public String getClientInfo(Socket s) {
        return " " + s.getInetAddress().getHostAddress() + " " + s.getPort();
    }

    public HealthCheckModule getModule() {
        return module;
    }

    public void handle(NetModule owner, Socket s) {
        if (logger.isTraceEnabled()) {
            logger.trace("Healthcheck connection: " + " [" + getClientInfo(s) + "]");
        }

        byte[] data = null;
        // Read in the message request, headers, and data
        try {
            InternetHeaders headers = new InternetHeaders();
            List<String> request = new ArrayList<String>(2);
            data = HTTPUtil.readHTTP(s.getInputStream(), s.getOutputStream(), headers, request);
            if (logger.isDebugEnabled()) {
                logger.debug("HealthCheck received request: " + request.toString() + "\n\tHeaders: " + HTTPUtil.printHeaders(headers.getAllHeaders(), "==", ";;") + "\n\tData: " + data);
            }
            // Invoke the healthcheck
            List<String> failures = new HealthCheck().runCheck(module);

            if (failures == null || failures.isEmpty()) {
                // For now just return OK
                HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_OK, null);
                if (logger.isTraceEnabled()) {
                    logger.trace("Healthcheck executed successfully: " + " [" + getClientInfo(s) + "]");
                }
            } else {
                // Must be failures so...

                // TODO: Maybe should implement mechanism to use a param to indicate if should return messages
                StringBuilder sb = new StringBuilder("Healthcheck execution failed: ");
                for (int i = 0; i < failures.size(); i++) {
                    sb.append("\n\t").append(i).append(".").append(failures.get(i));
                }
                HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_INTERNAL_ERROR, sb.toString());
                if (logger.isTraceEnabled()) {
                    logger.trace(sb);
                }

            }

        } catch (Exception e) {
            try {
                HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_UNAVAILABLE, null);
            } catch (IOException e1) {
            }
            String msg = "Unhandled error condition receiving healthcheck.";
            logger.error(msg, e);
            return;
        }

    }
}
