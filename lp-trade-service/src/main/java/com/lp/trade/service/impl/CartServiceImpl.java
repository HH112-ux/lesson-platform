package com.lp.trade.service.impl;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.trade.client.CourseInternalClient;
import com.lp.trade.dto.CartItemView;
import com.lp.trade.dto.CartView;
import com.lp.trade.dto.CourseSnapshot;
import com.lp.trade.entity.CartItem;
import com.lp.trade.mapper.CartMapper;
import com.lp.trade.mapper.TradeOrderMapper;
import com.lp.trade.service.CartService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Default {@link CartService} implementation.
 *
 * <p>Course data always comes from course-service so the cart never trusts a stale snapshot for
 * sale status or price. The unique key {@code uk_user_course} is the final guard for two concurrent
 * adds of the same course.
 *
 * @author Caspian
 * @since 0.1.0
 */
@Service
public class CartServiceImpl implements CartService {

    private static final int STATUS_PUBLISHED = 2;

    private final CartMapper cartMapper;

    private final TradeOrderMapper tradeOrderMapper;

    private final CourseInternalClient courseInternalClient;

    /**
     * Creates the service with its dependencies.
     *
     * @param cartMapper cart persistence
     * @param tradeOrderMapper ownership probe
     * @param courseInternalClient course-service reader
     * @author Caspian
     * @since 0.1.0
     */
    public CartServiceImpl(
            CartMapper cartMapper,
            TradeOrderMapper tradeOrderMapper,
            CourseInternalClient courseInternalClient) {
        this.cartMapper = cartMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.courseInternalClient = courseInternalClient;
    }

    /**
     * Adds a course to the cart.
     *
     * @param userId authenticated user id
     * @param courseId course id
     * @throws BizException when the course is missing, not on sale or already owned
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void addItem(long userId, long courseId) {
        CourseSnapshot course = courseInternalClient.course(courseId);
        if (course.getStatus() != STATUS_PUBLISHED) {
            throw new BizException(ErrorCode.TRADE_UNAVAILABLE);
        }
        if (tradeOrderMapper.countOwned(userId, courseId) > 0) {
            throw new BizException(ErrorCode.COURSE_ALREADY_OWNED);
        }
        if (cartMapper.selectByUserAndCourse(userId, courseId) != null) {
            return;
        }
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setCourseId(courseId);
        item.setPriceSnapshot(BigDecimal.valueOf(course.getPrice(), 2));
        try {
            cartMapper.insert(item);
        } catch (DuplicateKeyException exception) {
            // A concurrent add of the same course already won; the cart must still hold one row.
        }
    }

    /**
     * Returns the cart with current course data.
     *
     * @param userId authenticated user id
     * @return cart view, empty when nothing was added
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public CartView cart(long userId) {
        List<CartItem> items = cartMapper.selectByUser(userId);
        if (items.isEmpty()) {
            return new CartView(List.of());
        }
        List<Long> courseIds = items.stream().map(CartItem::getCourseId).toList();
        Map<Long, CourseSnapshot> courses =
                courseInternalClient.courses(courseIds).stream()
                        .collect(
                                Collectors.toMap(
                                        CourseSnapshot::getCourseId,
                                        Function.identity(),
                                        (first, second) -> first));
        List<CartItemView> lines =
                items.stream()
                        .map(item -> line(item, courses.get(item.getCourseId())))
                        .filter(java.util.Objects::nonNull)
                        .toList();
        return new CartView(lines);
    }

    /**
     * Removes a course from the cart; an absent row is treated as success.
     *
     * @param userId authenticated user id
     * @param courseId course id
     * @author Caspian
     * @since 0.1.0
     */
    @Override
    public void removeItem(long userId, long courseId) {
        cartMapper.deleteByUserAndCourse(userId, courseId);
    }

    private CartItemView line(CartItem item, CourseSnapshot course) {
        if (course == null) {
            return null;
        }
        return new CartItemView(
                course.getCourseId(),
                course.getTitle(),
                course.getCoverUrl(),
                BigDecimal.valueOf(course.getPrice(), 2));
    }
}
