/**
 * Teacher course endpoints used by the lesson video screen.
 *
 * @author Caspian
 * @since 0.9.0
 */
import { apiRequest } from './client'

/** One lesson of the teacher console, aligned with {@code TeacherLessonView}. */
export interface TeacherLesson {
  lessonId: number
  chapterId: number
  title: string
  /** MinIO object key, empty until the upload is confirmed. */
  videoUrl: string
  /** Duration in seconds, zero until the upload is confirmed. */
  durationSec: number
  /** Codec written by the confirm endpoint. */
  videoCodec: string
  videoWidth: number
  videoHeight: number
  videoSizeBytes: number
  free: boolean
  sort: number
  /** 1 visible, 0 hidden. */
  status: number
}

/** One chapter with its lessons, aligned with {@code TeacherChapterView}. */
export interface TeacherChapter {
  chapterId: number
  title: string
  sort: number
  /** 1 visible, 0 hidden. */
  status: number
  lessons: TeacherLesson[]
}

/** One course of the teacher console, aligned with {@code TeacherCourseView}. */
export interface TeacherCourse {
  courseId: number
  title: string
  /** 0 draft, 1 pending review, 2 published, 3 offline. */
  status: number
  lessonCount: number
}

/** Page of the teacher's own courses. */
export interface TeacherCoursePage {
  items: TeacherCourse[]
  total: number
  page: number
  size: number
}

/** Presigned upload credential, aligned with {@code TeacherUploadSessionView}. */
export interface TeacherUploadSession {
  uploadId: number
  lessonId: number
  objectKey: string
  /** Presigned PUT URL, valid until {@code expiresAt}. */
  uploadUrl: string
  expiresAt: string
  /** Declared size the server will accept, the only size the client must trust. */
  expectedSizeBytes: number
}

/**
 * Lists the courses owned by the signed-in teacher.
 *
 * @param status optional course status filter, {@code undefined} for all
 * @param pageNum one-based page number
 * @param pageSize page size, the backend caps it at 100
 * @returns one page of owned courses
 * @author Caspian
 * @since 0.9.0
 */
export function listTeacherCourses(
  status: number | undefined,
  pageNum: number,
  pageSize: number,
): Promise<TeacherCoursePage> {
  const params = new URLSearchParams({
    pageNum: String(pageNum),
    pageSize: String(pageSize),
  })
  if (status !== undefined) {
    params.set('status', String(status))
  }
  return apiRequest<TeacherCoursePage>(`/api/v1/teacher/courses?${params.toString()}`)
}

/**
 * Lists the chapters of a course with their lessons.
 *
 * @param courseId course identifier
 * @returns chapters ordered by sort
 * @author Caspian
 * @since 0.9.0
 */
export function listTeacherChapters(courseId: number): Promise<TeacherChapter[]> {
  return apiRequest<TeacherChapter[]>(`/api/v1/teacher/courses/${courseId}/chapters`)
}

/**
 * Opens an upload session for the video of one lesson.
 *
 * @param courseId course identifier
 * @param chapterId chapter identifier
 * @param lessonId lesson identifier
 * @param expectedSizeBytes size of the picked file in bytes
 * @returns session with the presigned PUT URL
 * @author Caspian
 * @since 0.9.0
 */
export function createLessonUploadSession(
  courseId: number,
  chapterId: number,
  lessonId: number,
  expectedSizeBytes: number,
): Promise<TeacherUploadSession> {
  return apiRequest<TeacherUploadSession>(
    `/api/v1/teacher/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}/upload-sessions`,
    {
      method: 'POST',
      body: JSON.stringify({ expectedSizeBytes }),
    },
  )
}

/**
 * Confirms an uploaded video and stores its probed facts.
 *
 * @param courseId course identifier
 * @param chapterId chapter identifier
 * @param lessonId lesson identifier
 * @param uploadId session identifier returned by {@link createLessonUploadSession}
 * @returns the confirmed lesson with the stored video metadata
 * @author Caspian
 * @since 0.9.0
 */
export function confirmLessonUpload(
  courseId: number,
  chapterId: number,
  lessonId: number,
  uploadId: number,
): Promise<TeacherLesson> {
  return apiRequest<TeacherLesson>(
    `/api/v1/teacher/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}/upload-sessions/${uploadId}/confirm`,
    { method: 'POST' },
  )
}
