package com.lp.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Course domain service entry point on port 8102.
 *
 * <p>Owns categories, courses, chapters, lessons, publishing and search. Teacher facing
 * operations under {@code /api/v1/teacher/**} also belong to this service because they act on
 * course content. The skeleton exposes health only; runtime configuration comes from Nacos once
 * the config centre client is wired in, and from the local defaults in {@code application.yml}
 * until then.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@SpringBootApplication
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
