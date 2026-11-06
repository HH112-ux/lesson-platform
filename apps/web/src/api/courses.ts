/**
 * Course catalog endpoints of the gateway.
 *
 * <p>The catalog is anonymous friendly: the gateway forwards the access token when present so the
 * backend can fill {@code owned}, and still answers when the visitor is signed out.</p>
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest } from './client'

/** Card projection returned by the catalog list, aligned with {@code PublicCourseView}. */
export interface CourseCard {
  courseId: number
  title: string
  coverUrl?: string | null
  /** Price in cents, aligned with the course service. */
  price: number
  /** 0 draft / 1 pending / 2 published / 3 offline. */
  status: number
  lessonCount: number
  instructorName?: string | null
  /** True when the signed-in visitor already owns the course. */
  owned?: boolean
}

/** Page of the catalog, aligned with {@code CoursePageView}. */
export interface CoursePage {
  items: CourseCard[]
  total: number
  page: number
  size: number
}

/** Category entry of {@code GET /api/v1/categories}. */
export interface Category {
  categoryId: number
  name: string
  slug: string
}

/** Filters of {@code GET /api/v1/courses}. */
export interface CourseQuery {
  keyword?: string
  /** Category id or slug; ids are used by the catalog filters. */
  categoryId?: number | null
  page: number
  size: number
}

/**
 * Loads one page of the catalog.
 *
 * @param query filters and paging
 * @returns the page of course cards
 * @author Caspian
 * @since 0.2.0
 */
export function listCourses(query: CourseQuery): Promise<CoursePage> {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  })
  if (query.keyword) {
    params.set('keyword', query.keyword)
  }
  if (typeof query.categoryId === 'number') {
    params.set('category', String(query.categoryId))
  }
  return apiRequest<CoursePage>(`/api/v1/courses?${params.toString()}`)
}

/**
 * Loads the flat category list used by the catalog filters.
 *
 * @returns categories in display order
 * @author Caspian
 * @since 0.2.0
 */
export function listCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/api/v1/categories')
}

/**
 * Loads the public detail of one course.
 *
 * @param courseId course identifier
 * @returns the same projection as the catalog cards, including {@code owned}
 * @author Caspian
 * @since 0.2.0
 */
export function getCourse(courseId: number): Promise<CourseCard> {
  return apiRequest<CourseCard>(`/api/v1/courses/${courseId}`)
}

/** Lesson entry of a chapter; {@code free} marks a preview lesson. */
export interface ChapterLesson {
  lessonId: number
  title: string
  free?: boolean
  sort?: number
}

/** Chapter with its lessons. */
export interface ChapterNode {
  chapterId: number
  title: string
  lessons: ChapterLesson[]
}

/** Response of {@code GET /api/v1/courses/{courseId}/chapters}. */
export interface ChapterList {
  chapters: ChapterNode[]
}

/**
 * Loads the chapter outline of a course.
 *
 * @param courseId course identifier
 * @returns chapters and their lessons
 * @author Caspian
 * @since 0.2.0
 */
export function listChapters(courseId: number): Promise<ChapterList> {
  return apiRequest<ChapterList>(`/api/v1/courses/${courseId}/chapters`)
}
