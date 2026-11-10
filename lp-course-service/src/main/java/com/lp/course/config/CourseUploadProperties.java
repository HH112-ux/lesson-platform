package com.lp.course.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the presigned lesson upload pipeline.
 *
 * <p>Credentials are supplied through environment variables and have no non-empty production
 * default, so a deployment without MinIO credentials fails with {@code 50206} instead of handing
 * out unusable URLs.
 *
 * @param sessionTtl lifetime of one presigned PUT credential, thirty minutes by default
 * @param maxSizeBytes largest accepted declared size, the sole runtime ceiling
 * @param minioEndpoint endpoint used for signing, reachable from the browser
 * @param minioRegion signing region, set explicitly to avoid a bucket location probe
 * @param minioAccessKey access key, blank when the deployment is not configured
 * @param minioSecretKey secret key, blank when the deployment is not configured
 * @param minioBucket bucket that stores lesson videos
 * @author Caspian
 * @since 0.7.0
 */
@ConfigurationProperties(prefix = "lp.course.upload")
public record CourseUploadProperties(
        Duration sessionTtl,
        long maxSizeBytes,
        String minioEndpoint,
        String minioRegion,
        String minioAccessKey,
        String minioSecretKey,
        String minioBucket) {}
