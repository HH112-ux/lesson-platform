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
  /** Whether a confirmed video exists; the object key never leaves the server. */
  hasVideo: boolean
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
  subtitle: string | null
  coverUrl: string
  description: string | null
  /** Proposed price in fen, the response unit of this endpoint. */
  price: number
  /** Crossed out price in fen, {@code null} when unset. */
  originalPrice: number | null
  categoryId: number | null
  level: number
  /** 0 draft, 1 pending review, 2 published, 3 offline. */
  status: number
  reviewRemark: string | null
  lessonCount: number
  submittedAt: string | null
  updatedAt: string
}

/** Form payload of the teacher course endpoints, aligned with {@code TeacherCourseRequest}. */
export interface TeacherCoursePayload {
  categoryId?: number
  title?: string
  subtitle?: string
  coverUrl?: string
  description?: string
  /** Proposed price in yuan; the server converts, the console never sends fen. */
  price?: number
  /** Crossed out price in yuan; the server converts. */
  originalPrice?: number
  /** Set true to remove the crossed out price; omit {@code originalPrice} when set. */
  clearOriginalPrice?: boolean
  level?: number
}

/** Category entry of the public catalog, reused by the teacher course form. */
export interface Category {
  categoryId: number
  name: string
  slug: string
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

/** Presigned playback credential, aligned with {@code TeacherPlayUrlView}. */
export interface TeacherPlayUrl {
  playUrl: string
  expiresIn: number
  expiresAt: number
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
 * Creates a course draft owned by the signed-in teacher.
 *
 * @param payload form fields, price and original price in yuan
 * @returns the created course, status draft
 * @author Caspian
 * @since 0.11.0
 */
export function createTeacherCourse(payload: TeacherCoursePayload): Promise<TeacherCourse> {
  return apiRequest<TeacherCourse>('/api/v1/teacher/courses', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * Updates an editable course and its proposed price.
 *
 * <p>Omitted fields keep their previous value, so the price is sent only when the teacher enters a
 * new one. Editing an offline course moves it back to draft.
 *
 * @param courseId course identifier
 * @param payload form fields, price and original price in yuan
 * @returns the updated course
 * @author Caspian
 * @since 0.11.0
 */
export function updateTeacherCourse(
  courseId: number,
  payload: TeacherCoursePayload,
): Promise<TeacherCourse> {
  return apiRequest<TeacherCourse>(`/api/v1/teacher/courses/${courseId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/**
 * Lists the public category catalog for the course form.
 *
 * @returns categories ordered by sort
 * @author Caspian
 * @since 0.11.0
 */
export function listCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/api/v1/categories')
}

/**
 * Submits a draft or offline course for operations review.
 *
 * <p>The backend rejects the call while any visible lesson has no confirmed video, so the screen
 * shows that rule verbatim instead of blocking the button.
 *
 * @param courseId course identifier
 * @returns the course in pending review status
 * @author Caspian
 * @since 0.12.0
 */
export function submitTeacherCourse(courseId: number): Promise<TeacherCourse> {
  return apiRequest<TeacherCourse>(`/api/v1/teacher/courses/${courseId}/submit`, {
    method: 'POST',
  })
}

/**
 * Withdraws the caller's own pending course back to draft.
 *
 * <p>Only the owning teacher may call this; operations users get 40301. Courses that are no longer
 * pending get 40901.
 *
 * @param courseId course identifier
 * @returns the course back in draft status
 * @author Caspian
 * @since 0.13.0
 */
export function withdrawTeacherCourse(courseId: number): Promise<TeacherCourse> {
  return apiRequest<TeacherCourse>(`/api/v1/teacher/courses/${courseId}/withdraw`, {
    method: 'POST',
  })
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

/**
 * Signs a short lived playback URL for a lesson video; the console never receives the object key.
 *
 * @param courseId course identifier
 * @param chapterId chapter identifier
 * @param lessonId lesson identifier
 * @returns presigned GET URL with its lifetime and expiry
 * @author Caspian
 * @since 0.10.0
 */
export function getLessonPlayUrl(
  courseId: number,
  chapterId: number,
  lessonId: number,
): Promise<TeacherPlayUrl> {
  return apiRequest<TeacherPlayUrl>(
    `/api/v1/teacher/courses/${courseId}/chapters/${chapterId}/lessons/${lessonId}/play-url`,
  )
}
