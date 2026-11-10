package com.lp.trade.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.dto.AccountDeletionStateView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Verifies the cooling-off state client maps the auth answer and fails closed.
 *
 * <p>Failing closed is the point of the class: an unreadable answer must become {@code 50401} and
 * never a permissive default, because a permissive default would let a disappearing account buy.
 *
 * @author Caspian
 * @since 0.3.0
 */
class AccountDeletionStateClientTest {

    private static final long USER_ID = 1001L;

    private static final String EXPECTED_URL =
            "http://auth.local/internal/v1/account-deletion/state?userId=" + USER_ID;

    private MockRestServiceServer server;

    private AccountDeletionStateClient client;

    /**
     * Builds a client over a mocked transport so no socket is opened.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://auth.local");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountDeletionStateClient(builder.build());
    }

    /**
     * Maps a successful envelope into the local view.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void returnsStateView() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(
                        withSuccess(
                                "{\"code\":0,\"message\":\"ok\",\"traceId\":\"t\","
                                        + "\"data\":{\"userId\":1001,\"coolingOff\":true,"
                                        + "\"effectiveAt\":\"2026-11-17T10:00:00\"}}",
                                MediaType.APPLICATION_JSON));

        AccountDeletionStateView view = client.state(USER_ID);

        assertThat(view.userId()).isEqualTo(USER_ID);
        assertThat(view.coolingOff()).isTrue();
        assertThat(view.effectiveAt()).isEqualTo("2026-11-17T10:00:00");
        server.verify();
    }

    /**
     * Turns an upstream server error into {@code 50401} instead of a permissive answer.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void failsClosedWhenAuthServiceErrors() {
        server.expect(requestTo(EXPECTED_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.state(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        server.verify();
    }

    /**
     * Turns a non zero envelope into {@code 50401} as well.
     *
     * @author Caspian
     * @since 0.3.0
     */
    @Test
    void failsClosedWhenEnvelopeCarriesAnErrorCode() {
        server.expect(requestTo(EXPECTED_URL))
                .andRespond(
                        withSuccess(
                                "{\"code\":40001,\"message\":\"invalid request parameter\"}",
                                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.state(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE.getCode());
        server.verify();
    }
}
