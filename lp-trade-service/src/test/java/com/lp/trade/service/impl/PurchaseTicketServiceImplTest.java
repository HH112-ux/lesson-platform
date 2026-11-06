package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.config.TradeProperties;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.dto.PurchaseTicketView;
import com.lp.trade.dto.RedeemTicketView;
import com.lp.trade.purchase.AuthSessionClient;
import com.lp.trade.purchase.PurchaseTicketPayload;
import com.lp.trade.purchase.PurchaseTicketStore;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the purchase ticket flow; Redis, course-service and auth-service are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class PurchaseTicketServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final long COURSE_ID = 7L;

    private static final String TICKET = "opaque-ticket";

    @Mock private PurchaseTicketStore ticketStore;

    @Mock private CourseInternalClient courseInternalClient;

    @Mock private AuthSessionClient authSessionClient;

    private PurchaseTicketServiceImpl service;

    /**
     * Builds the service with a fixed web host.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @BeforeEach
    void setUp() {
        TradeProperties properties = new TradeProperties();
        properties.setWebBaseUrl("https://learn.example.com");
        service =
                new PurchaseTicketServiceImpl(
                        ticketStore, courseInternalClient, authSessionClient, properties);
    }

    /**
     * The issued URL carries only the ticket, never the user id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issueBuildsTicketUrl() {
        when(courseInternalClient.course(COURSE_ID)).thenReturn(publishedCourse());
        when(ticketStore.issue(USER_ID, COURSE_ID)).thenReturn(TICKET);

        PurchaseTicketView view = service.issue(USER_ID, COURSE_ID);

        assertEquals("https://learn.example.com/purchase?ticket=" + TICKET, view.getUrl());
        assertEquals(view.getUrl(), view.getQrPayload());
        assertEquals(TICKET, view.getTicket());
        assertEquals(false, view.getUrl().contains("userId"));
        assertEquals(false, view.getUrl().contains(Long.toString(USER_ID)));
    }

    /**
     * A missing course propagates the documented not-found code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issueRejectsMissingCourse() {
        when(courseInternalClient.course(COURSE_ID)).thenThrow(new BizException(40401, "missing"));

        BizException exception =
                assertThrows(BizException.class, () -> service.issue(USER_ID, COURSE_ID));

        assertEquals(40401, exception.getCode());
        verifyNoInteractions(ticketStore);
    }

    /**
     * An offline course cannot be ticketed.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void issueRejectsOfflineCourse() {
        CourseSnapshot course = publishedCourse();
        course.setStatus(3);
        when(courseInternalClient.course(COURSE_ID)).thenReturn(course);

        BizException exception =
                assertThrows(BizException.class, () -> service.issue(USER_ID, COURSE_ID));

        assertEquals(61001, exception.getCode());
        verifyNoInteractions(ticketStore);
    }

    /**
     * A valid ticket yields a token pair and the bound course.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void redeemReturnsTokens() {
        when(ticketStore.peek(TICKET)).thenReturn(Optional.of(payload()));
        when(ticketStore.take(TICKET)).thenReturn(Optional.of(payload()));
        when(authSessionClient.issue(USER_ID)).thenReturn(tokens());

        RedeemTicketView view = service.redeem(TICKET, null);

        assertEquals("access", view.getAccessToken());
        assertEquals("refresh", view.getRefreshToken());
        assertEquals(900L, view.getExpiresIn());
        assertEquals(COURSE_ID, view.getCourseId());
        verify(ticketStore).markUsed(TICKET);
    }

    /**
     * Expired, used and unknown tickets each map to their own code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void redeemClassifiesMissingTickets() {
        when(ticketStore.peek(TICKET)).thenReturn(Optional.empty());
        when(ticketStore.classify(TICKET))
                .thenReturn(PurchaseTicketStore.Absence.EXPIRED)
                .thenReturn(PurchaseTicketStore.Absence.USED)
                .thenReturn(PurchaseTicketStore.Absence.UNKNOWN);

        assertEquals(
                62012,
                assertThrows(BizException.class, () -> service.redeem(TICKET, null)).getCode());
        assertEquals(
                62013,
                assertThrows(BizException.class, () -> service.redeem(TICKET, null)).getCode());
        assertEquals(
                62014,
                assertThrows(BizException.class, () -> service.redeem(TICKET, null)).getCode());
        verify(authSessionClient, never()).issue(anyLong());
    }

    /**
     * Another signed-in user cannot consume somebody else's ticket.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void redeemRejectsAnotherUser() {
        when(ticketStore.peek(TICKET)).thenReturn(Optional.of(payload()));

        BizException exception =
                assertThrows(BizException.class, () -> service.redeem(TICKET, 2002L));

        assertEquals(62015, exception.getCode());
        verify(ticketStore, never()).take(anyString());
        verify(ticketStore, never()).markUsed(anyString());
        verifyNoInteractions(authSessionClient);
    }

    /**
     * A blank ticket is reported as unknown, not as a server error.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void redeemRejectsBlankTicket() {
        BizException exception = assertThrows(BizException.class, () -> service.redeem(" ", null));

        assertEquals(62014, exception.getCode());
        verifyNoInteractions(ticketStore);
    }

    /**
     * Builds a published course snapshot.
     *
     * @return snapshot
     * @author Caspian
     * @since 0.1.0
     */
    private CourseSnapshot publishedCourse() {
        CourseSnapshot course = new CourseSnapshot();
        course.setCourseId(COURSE_ID);
        course.setTitle("Course");
        course.setPrice(9900L);
        course.setStatus(2);
        course.setTeacherId(55L);
        return course;
    }

    /**
     * Builds a valid payload.
     *
     * @return payload
     * @author Caspian
     * @since 0.1.0
     */
    private PurchaseTicketPayload payload() {
        return new PurchaseTicketPayload(USER_ID, COURSE_ID, System.currentTimeMillis() + 60_000L);
    }

    /**
     * Builds a token pair.
     *
     * @return tokens
     * @author Caspian
     * @since 0.1.0
     */
    private AuthSessionClient.SessionTokens tokens() {
        AuthSessionClient.SessionTokens tokens = new AuthSessionClient.SessionTokens();
        tokens.setAccessToken("access");
        tokens.setRefreshToken("refresh");
        tokens.setExpiresIn(900L);
        return tokens;
    }
}
