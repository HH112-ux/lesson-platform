package com.lp.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lp.auth.dto.UserMetricsView;
import com.lp.auth.mapper.AuthUserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the learner window metrics.
 *
 * @author Caspian
 * @since 0.21.0
 */
@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock private AuthUserMapper authUserMapper;

    @InjectMocks private UserStatsService service;

    /**
     * The metrics expose the learner figures and query the learner statements only.
     *
     * @author Caspian
     * @since 0.21.0
     */
    @Test
    void returnsLearnerCounts() {
        when(authUserMapper.countStudents()).thenReturn(42L);
        when(authUserMapper.countStudentsCreatedSince(any())).thenReturn(7L);

        UserMetricsView view = service.metrics(30);

        assertEquals(42L, view.totalStudents());
        assertEquals(7L, view.newStudents());
        verify(authUserMapper).countStudents();
        verify(authUserMapper).countStudentsCreatedSince(any());
    }

    /**
     * The window is clamped to the accepted range.
     *
     * @author Caspian
     * @since 0.21.0
     */
    @Test
    void clampsWindowLength() {
        when(authUserMapper.countStudents()).thenReturn(0L);
        when(authUserMapper.countStudentsCreatedSince(any())).thenReturn(0L);

        LocalDate today = LocalDate.now();
        service.metrics(0);
        service.metrics(365);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(authUserMapper, org.mockito.Mockito.times(2))
                .countStudentsCreatedSince(captor.capture());

        assertEquals(today.atStartOfDay(), captor.getAllValues().get(0));
        assertEquals(today.minusDays(89).atStartOfDay(), captor.getAllValues().get(1));
    }
}
