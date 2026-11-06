/**
 * Course catalog endpoints of the gateway.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { apiRequest } from './client'

/** Card projection returned by the catalog list. */
export interface CourseCard {
  courseId: number
  title: string
  coverUrl?: string | null
  /** Price in cents, aligned with the course service. */
  price: number
  lessonCount?: number
  /** 0 draft / 1 pending / 2 published / 3 offline. */
  status?: number
}

/** Offset page shared by every list endpoint. */
export interface PageView<T> {
  pageNum: number
  pageSize: number
  total: number
  records: T[]
}

/** Node of the category tree. */
export interface CategoryNode {
  id: number
  name: string
  children?: CategoryNode[]
}

/** Filters of {@code GET /api/v1/courses}. */
export interface CourseQuery {
  keyword?: string
  categoryId?: number | null
  pageNum: number
  pageSize: number
}

/**
 * Loads one page of the catalog.
 *
 * @param query filters and paging
 * @returns the page of course cards
 * @author Caspian
 * @since 0.2.0
 */
export function listCourses(query: CourseQuery): Promise<PageView<CourseCard>> {
  const params = new URLSearchParams({
    pageNum: String(query.pageNum),
    pageSize: String(query.pageSize),
  })
  if (query.keyword) {
    params.set('keyword', query.keyword)
  }
  if (typeof query.categoryId === 'number') {
    params.set('categoryId', String(query.categoryId))
  }
  return apiRequest<PageView<CourseCard>>(`/api/v1/courses?${params.toString()}`)
}

/**
 * Loads the category tree used by the catalog filters.
 *
 * @returns category roots, each optionally carrying children
 * @author Caspian
 * @since 0.2.0
 */
export function listCategoryTree(): Promise<CategoryNode[]> {
  return apiRequest<CategoryNode[]>('/api/v1/categories/tree')
}
