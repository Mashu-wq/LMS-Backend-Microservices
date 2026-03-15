package com.lms.progressservice.application.port;

import java.util.Optional;

/**
 * Port for retrieving course metadata from course-service.
 * Used to fetch the total lesson count when initialising progress.
 */
public interface CourseServiceClient {

    /**
     * Returns the total number of published lessons in the course,
     * or {@link Optional#empty()} if the course-service is unavailable.
     */
    Optional<Integer> getTotalLessons(String courseId);
}
