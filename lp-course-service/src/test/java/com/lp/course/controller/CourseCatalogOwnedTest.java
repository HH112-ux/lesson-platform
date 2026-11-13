package com.lp.course.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lp.common.identity.IdentityHeaders;
import com.lp.course.client.LearnEntitlementClient;
import com.lp.course.entity.Course;
import com.lp.course.mapper.CourseCategoryMapper;
import com.lp.course.mapper.CourseMapper;
import com.lp.course.service.CourseInternalService;
import com.lp.course.service.impl.CoursePublicServiceImpl;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Covers the signed-in catalog path against a real HTTP learn-service stub.
 *
 * <p>The catalog only calls the entitlement lookup when the gateway injects a user id, so an
 * anonymous request never exercises the RestClient and its JSON conversion. This test keeps the
 * signed-in branch honest: before the Jackson 3 target type was adopted, the response could not be
 * deserialized and the endpoint failed with {@code 50001} in production.
 *
 * @author Caspian
 * @since 1.0.0
 */
class CourseCatalogOwnedTest {

    private HttpServer learnServer;

    private MockMvc mockMvc;

    /**
     * Starts the learn-service stub and wires the real client and service under test.
     *
     * @throws IOException when the stub port cannot be bound
     * @author Caspian
     * @since 1.0.0
     */
    @BeforeEach
    void setUp() throws IOException {
        learnServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        learnServer.createContext(
                "/internal/v1/entitlements/owned",
                exchange -> {
                    byte[] payload =
                            "{\"code\":0,\"message\":\"ok\",\"data\":{\"courseIds\":[8]}}"
                                    .getBytes(UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, payload.length);
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(payload);
                    }
                });
        learnServer.start();

        LearnEntitlementClient client =
                new LearnEntitlementClient(
                        "http://127.0.0.1:" + learnServer.getAddress().getPort(),
                        "test-internal-token",
                        2000);

        CourseMapper courseMapper = mock(CourseMapper.class);
        when(courseMapper.countPublished(null, null)).thenReturn(1L);
        when(courseMapper.selectPublishedPage(null, null, 0L, 20))
                .thenReturn(List.of(publishedCourse()));

        CoursePublicServiceImpl service =
                new CoursePublicServiceImpl(
                        mock(CourseInternalService.class),
                        courseMapper,
                        mock(CourseCategoryMapper.class),
                        client);
        mockMvc = MockMvcBuilders.standaloneSetup(new CoursePublicController(service)).build();
    }

    /**
     * Stops the stub so the test leaves no listening socket behind.
     *
     * @author Caspian
     * @since 1.0.0
     */
    @AfterEach
    void tearDown() {
        learnServer.stop(0);
    }

    /**
     * A signed-in caller gets the catalog with the owned flag filled from learn-service.
     *
     * @throws Exception when MockMvc fails to perform the request
     * @author Caspian
     * @since 1.0.0
     */
    @Test
    void signedInCatalogMarksOwnedCourse() throws Exception {
        mockMvc.perform(get("/api/v1/courses").header(IdentityHeaders.USER_ID, "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].courseId").value(8))
                .andExpect(jsonPath("$.data.items[0].owned").value(true));
    }

    /**
     * Builds the published row returned by the stubbed mapper.
     *
     * @return published course row
     * @author Caspian
     * @since 1.0.0
     */
    private static Course publishedCourse() {
        Course course = new Course();
        course.setId(8L);
        course.setTitle("Course 8");
        course.setCoverUrl("cover-8");
        course.setPrice(new BigDecimal("99.00"));
        course.setStatus(2);
        course.setLessonCount(4L);
        course.setInstructorName("Teacher A");
        return course;
    }
}
