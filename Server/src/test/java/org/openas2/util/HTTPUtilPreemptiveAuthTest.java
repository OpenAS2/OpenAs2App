package org.openas2.util;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that outbound HTTP Basic auth is sent pre-emptively (on the first request) rather than
 * only in response to a 401 challenge. The mock server never challenges, so credentials arrive on
 * the first request only if the client sends them pre-emptively.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class HTTPUtilPreemptiveAuthTest {

    private HttpServer server;
    private String url;
    private final AtomicReference<String> firstRequestAuthHeader = new AtomicReference<String>();

    @BeforeAll
    public void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/as2", exchange -> {
            // Capture the Authorization header of the request and respond 200 without ever
            // sending a 401 challenge.
            firstRequestAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(null);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/as2";
    }

    @AfterAll
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Map<String, Object> options() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, "10000");
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT, "10000");
        return options;
    }

    @Test
    public void basicAuthIsSentPreemptivelyOnFirstRequest() throws Exception {
        firstRequestAuthHeader.set(null);
        Map<String, Object> options = options();
        options.put(HTTPUtil.PARAM_HTTP_USER, "as2user");
        options.put(HTTPUtil.PARAM_HTTP_PWD, "s3cret");

        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, options, 0L, false);

        assertEquals(200, resp.getStatusCode());
        String expected = "Basic " + Base64.getEncoder().encodeToString("as2user:s3cret".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, firstRequestAuthHeader.get(),
                "Basic credentials must be present on the first request (pre-emptive auth)");
    }

    @Test
    public void noAuthHeaderWhenNoCredentialsConfigured() throws Exception {
        firstRequestAuthHeader.set(null);

        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, options(), 0L, false);

        assertEquals(200, resp.getStatusCode());
        assertNull(firstRequestAuthHeader.get(), "no Authorization header should be sent without configured credentials");
    }
}
