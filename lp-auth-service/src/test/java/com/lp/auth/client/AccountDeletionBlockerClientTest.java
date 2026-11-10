package com.lp.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lp.auth.dto.AccountDeletionBlockerView;
import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies the blocker client maps the trade answer and fails closed.
 *
 * <p>Failing closed is the point of the class: an unreadable answer must become {@code 50401} and
 * never an empty blocker list, because an empty list would let a cancellation hide live orders.
 *
 * @author Caspian
 * @since 0.2.0
 */
class AccountDeletionBlockerClientTest {

    private static final long USER_ID = 2002L;

    private static final String EXPECTED_URL =
            "http://trade.local/internal/v1/account-deletion/blockers?userId=" + USER_ID;

    private MockRestServiceServer server;

    private AccountDeletionBlockerClient client;

    /**
     * Builds a client over a mocked transport so no socket is opened.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade.local");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountDeletionBlockerClient(builder.build());
    }

    /**
     * Maps a successful envelope into the local view.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void returnsBlockerView() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(
                        withSuccess(
                                "{\"code\":0,\"message\":\"ok\",\"traceId\":\"t\","
                                        + "\"data\":{\"userId\":2002,\"pendingOrders\":1,"
                                        + "\"refundingRequests\":0,\"blocked\":true}}",
                                MediaType.APPLICATION_JSON));

        AccountDeletionBlockerView view = client.blockers(USER_ID);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.pendingOrders()).isEqualTo(1);
        assertThat(view.blocked()).isTrue();
        server.verify();
    }

    /**
     * Turns an upstream server error into {@code 50401} instead of an empty blocker list.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void failsClosedWhenTradeServiceErrors() {
        server.expect(requestTo(EXPECTED_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.blockers(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        server.verify();
    }

    /**
     * Turns a non zero envelope into {@code 50401} as well.
     *
     * @author Caspian
     * @since 0.2.0
     */
    @Test
    void failsClosedWhenEnvelopeCarriesAnErrorCode() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(
                        withSuccess(
                                "{\"code\":40001,\"message\":\"invalid request parameter\"}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.blockers(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        server.verify();
    }
}
