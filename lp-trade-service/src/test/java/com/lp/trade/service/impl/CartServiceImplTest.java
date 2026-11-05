package com.lp.trade.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.dto.CartItemView;
import com.lp.trade.dto.CartView;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.entity.CartItem;
import com.lp.trade.mapper.CartMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * Unit tests for the cart service; persistence and the course-service client are mocked.
 *
 * @author Caspian
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final long USER_ID = 1001L;

    @Mock private CartMapper cartMapper;

    @Mock private TradeOrderMapper tradeOrderMapper;

    @Mock private CourseInternalClient courseInternalClient;

    @InjectMocks private CartServiceImpl cartService;

    /**
     * The first add stores the current price and the user id.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void addsCourseOnFirstCall() {
        when(courseInternalClient.course(7L)).thenReturn(course(7L, 9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        when(cartMapper.selectByUserAndCourse(USER_ID, 7L)).thenReturn(null);

        cartService.addItem(USER_ID, 7L);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartMapper).insert(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(7L, captor.getValue().getCourseId());
        assertEquals(0, new BigDecimal("99.00").compareTo(captor.getValue().getPriceSnapshot()));
    }

    /**
     * Adding the same course twice inserts nothing more.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void repeatedAddIsIdempotent() {
        when(courseInternalClient.course(7L)).thenReturn(course(7L, 9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        when(cartMapper.selectByUserAndCourse(USER_ID, 7L)).thenReturn(item(7L));

        cartService.addItem(USER_ID, 7L);

        verify(cartMapper, never()).insert(any());
    }

    /**
     * A concurrent duplicate rejected by the unique key is a success.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void concurrentDuplicateIsTreatedAsSuccess() {
        when(courseInternalClient.course(7L)).thenReturn(course(7L, 9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(0L);
        when(cartMapper.selectByUserAndCourse(USER_ID, 7L)).thenReturn(null);
        when(cartMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_user_course"));

        cartService.addItem(USER_ID, 7L);
    }

    /**
     * A course already bought cannot be added again.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOwnedCourse() {
        when(courseInternalClient.course(7L)).thenReturn(course(7L, 9900L, 2));
        when(tradeOrderMapper.countOwned(USER_ID, 7L)).thenReturn(1L);

        BizException exception =
                assertThrows(BizException.class, () -> cartService.addItem(USER_ID, 7L));

        assertEquals(61002, exception.getCode());
        verify(cartMapper, never()).insert(any());
    }

    /**
     * An offline course cannot be added.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void rejectsOfflineCourse() {
        when(courseInternalClient.course(7L)).thenReturn(course(7L, 9900L, 3));

        BizException exception =
                assertThrows(BizException.class, () -> cartService.addItem(USER_ID, 7L));

        assertEquals(62003, exception.getCode());
        verify(cartMapper, never()).insert(any());
    }

    /**
     * A missing course keeps the course-service code.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void propagatesMissingCourse() {
        when(courseInternalClient.course(99L)).thenThrow(new BizException(ErrorCode.NOT_FOUND));

        BizException exception =
                assertThrows(BizException.class, () -> cartService.addItem(USER_ID, 99L));

        assertEquals(40401, exception.getCode());
    }

    /**
     * Deleting an absent row is a success.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void removingAbsentItemSucceeds() {
        when(cartMapper.deleteByUserAndCourse(USER_ID, 99L)).thenReturn(0);

        cartService.removeItem(USER_ID, 99L);

        verify(cartMapper).deleteByUserAndCourse(USER_ID, 99L);
    }

    /**
     * The cart view uses current course price and skips courses that disappeared.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void cartUsesCurrentCourseData() {
        when(cartMapper.selectByUser(USER_ID)).thenReturn(List.of(item(7L), item(8L)));
        when(courseInternalClient.courses(List.of(7L, 8L)))
                .thenReturn(List.of(course(7L, 9900L, 2)));

        CartView view = cartService.cart(USER_ID);

        assertEquals(1, view.getItems().size());
        CartItemView line = view.getItems().get(0);
        assertEquals(7L, line.getCourseId());
        assertEquals(0, new BigDecimal("99.00").compareTo(line.getPrice()));
    }

    /**
     * An empty cart never calls course-service.
     *
     * @author Caspian
     * @since 0.1.0
     */
    @Test
    void emptyCartSkipsUpstream() {
        when(cartMapper.selectByUser(anyLong())).thenReturn(List.of());

        CartView view = cartService.cart(USER_ID);

        assertEquals(0, view.getItems().size());
        verify(courseInternalClient, never()).courses(any());
    }

    private CourseSnapshot course(long courseId, long priceFen, int status) {
        CourseSnapshot snapshot = new CourseSnapshot();
        snapshot.setCourseId(courseId);
        snapshot.setTitle("Course " + courseId);
        snapshot.setCoverUrl("cover-" + courseId);
        snapshot.setPrice(priceFen);
        snapshot.setStatus(status);
        snapshot.setTeacherId(3L);
        snapshot.setLessonCount(4L);
        return snapshot;
    }

    private CartItem item(long courseId) {
        CartItem item = new CartItem();
        item.setUserId(USER_ID);
        item.setCourseId(courseId);
        item.setPriceSnapshot(new BigDecimal("99.00"));
        return item;
    }
}
