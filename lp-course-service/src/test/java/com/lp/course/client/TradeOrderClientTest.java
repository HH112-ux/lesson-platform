package com.lp.course.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the trade paid-state response is readable with the Boot 4 JSON stack.
 *
 * <p>The teacher course detail and the sold-course content guard both depend on this call; a
 * response that cannot be deserialized fails the request instead of falling back to null.
 *
 * @author Caspian
 * @since 0.15.0
 */
class TradeOrderClientTest {

    private HttpServer tradeServer;

    private TradeOrderClient client;

    /**
     * Starts a trade-service stub returning a paid state for course 9.
     *
     * @throws IOException when the stub port cannot be bound
     * @author Caspian
     * @since 0.15.0
     */
    @BeforeEach
    void setUp() throws IOException {
        tradeServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tradeServer.createContext(
                "/internal/v1/orders/paid-exists",
                exchange -> {
                    byte[] payload =
                            "{\"code\":0,\"message\":\"ok\",\"data\":true}".getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, payload.length);
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(payload);
                    }
                });
        tradeServer.start();
        client =
                new TradeOrderClient(
                        "http://127.0.0.1:" + tradeServer.getAddress().getPort(),
                        "test-internal-token",
                        2000);
    }

    /**
     * Stops the stub so the test leaves no listening socket behind.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @AfterEach
    void tearDown() {
        tradeServer.stop(0);
    }

    /**
     * Reads the boolean paid state from a real HTTP response.
     *
     * @author Caspian
     * @since 0.15.0
     */
    @Test
    void readsPaidState() {
        assertEquals(Boolean.TRUE, client.paidOrderExists(9L));
    }
}
