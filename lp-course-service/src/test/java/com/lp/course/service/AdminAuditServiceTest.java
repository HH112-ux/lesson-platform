package com.lp.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lp.common.audit.AuditAction;
import com.lp.common.audit.AuditObject;
import com.lp.common.audit.OperatorRole;
import com.lp.common.exception.BizException;
import com.lp.course.dto.AuditLogCreateRequest;
import com.lp.course.entity.AdminAuditLog;
import com.lp.course.mapper.AdminAuditLogMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of the unified audit writer.
 *
 * @author Caspian
 * @since 0.6.0
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    private static final LocalDateTime DECIDED_AT = LocalDateTime.of(2026, 11, 9, 10, 30);

    @Mock private AdminAuditLogMapper adminAuditLogMapper;

    private AdminAuditService service;

    /**
     * Builds the service under test.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @BeforeEach
    void setUp() {
        service = new AdminAuditService(adminAuditLogMapper);
    }

    /**
     * A valid command is mapped onto the row with the decision time preserved.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void appendsValidatedEntry() {
        service.record(
                new AuditLogCreateRequest(
                        55L,
                        OperatorRole.ADMIN,
                        AuditAction.COURSE_APPROVE,
                        AuditObject.COURSE,
                        7L,
                        1,
                        2,
                        "  ok  ",
                        " trace-1 ",
                        null,
                        DECIDED_AT));

        AdminAuditLog row = captureInsert();
        assertEquals(55L, row.getOperatorId());
        assertEquals(OperatorRole.ADMIN, row.getOperatorRole());
        assertEquals(AuditAction.COURSE_APPROVE, row.getActionType());
        assertEquals(AuditObject.COURSE, row.getObjectType());
        assertEquals(7L, row.getObjectId());
        assertEquals(1, row.getFromStatus());
        assertEquals(2, row.getToStatus());
        assertEquals("ok", row.getRemark());
        assertEquals("trace-1", row.getTraceId());
        assertNull(row.getDedupeKey());
        assertEquals(DECIDED_AT, row.getCreatedAt());
    }

    /**
     * An unknown operator role is refused before any write.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void rejectsUnknownOperatorRole() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.record(
                                        new AuditLogCreateRequest(
                                                55L,
                                                9,
                                                AuditAction.COURSE_APPROVE,
                                                AuditObject.COURSE,
                                                7L,
                                                1,
                                                2,
                                                null,
                                                "trace-1",
                                                null,
                                                DECIDED_AT)));

        assertEquals(40001, exception.getCode());
        verify(adminAuditLogMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    /**
     * A missing trace id is refused because it would break log correlation.
     *
     * @author Caspian
     * @since 0.6.0
     */
    @Test
    void rejectsMissingTraceId() {
        BizException exception =
                assertThrows(
                        BizException.class,
                        () ->
                                service.record(
                                        new AuditLogCreateRequest(
                                                55L,
                                                OperatorRole.OPS,
                                                AuditAction.TEACHER_REJECT,
                                                AuditObject.TEACHER,
                                                7L,
                                                0,
                                                2,
                                                "bad",
                                                " ",
                                                null,
                                                DECIDED_AT)));

        assertEquals(40001, exception.getCode());
    }

    private AdminAuditLog captureInsert() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
