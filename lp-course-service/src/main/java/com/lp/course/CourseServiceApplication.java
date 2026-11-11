package com.lp.course;

import com.lp.common.identity.TrustedIdentityConfig;
import com.lp.common.internal.InternalTokenConfig;
import com.lp.common.web.GlobalExceptionHandler;
import com.lp.course.config.CourseUploadProperties;
import com.lp.course.config.LessonCleanupProperties;
import com.lp.course.config.LessonTranscodeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Course domain service entry point on port 8102.
 *
 * <p>Owns categories, courses, chapters, lessons, publishing and search. Teacher facing operations
 * under {@code /api/v1/teacher/**} also belong to this service because they act on course content.
 * Service-to-service reads live under {@code /internal/v1/**} and bypass the gateway. Runtime
 * configuration comes from Nacos once the config centre client is wired in, and from the local
 * defaults in {@code application.yml} until then.
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
@MapperScan("com.lp.course.mapper")
@EnableConfigurationProperties({
    CourseUploadProperties.class,
    LessonTranscodeProperties.class,
    LessonCleanupProperties.class
})
@Import({GlobalExceptionHandler.class, InternalTokenConfig.class, TrustedIdentityConfig.class})
@EnableScheduling
public class CourseServiceApplication {

    /**
     * Starts the course service.
     *
     * @param args command line arguments passed to Spring Boot
     * @author Caspian
     * @since 0.1.0
     */
    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}
