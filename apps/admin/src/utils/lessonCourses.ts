/**
 * Course card helpers of the lesson video screen.
 *
 * <p>The teacher course list only carries the lesson count, so the chapter count of a card is
 * derived from the existing chapter endpoint. Requests are bounded, time limited and optional: a
 * slow or failing chapter call only hides one number on one card.</p>
 *
 * @author Caspian
 * @since 0.16.0
 */
import type { TeacherChapter } from '../api/teacher'

/** Chapter count per course id; {@code null} means the chapters could not be loaded in time. */
export type ChapterCounts = Map<number, number | null>

/** Courses above this count skip the card chapter lookup and render a placeholder instead. */
export const MAX_CARD_CHAPTER_COUNT = 20

/** Options of {@link loadChapterCounts}. */
export interface ChapterCountOptions {
  /** Counts already known in this session, reused instead of calling the endpoint again. */
  cached?: ReadonlyMap<number, number | null>
  /** Maximum parallel requests, clamped to at least one. */
  concurrency?: number
  /** Per request ceiling; a slower call counts as a failure. */
  timeoutMs?: number
}

/**
 * Tells whether a list of this size may look up chapter counts for its cards.
 *
 * @param courseCount number of courses on the screen
 * @returns true when the list is small enough to pay for the extra requests
 * @author Caspian
 * @since 0.16.0
 */
export function shouldLoadCardChapterCounts(courseCount: number): boolean {
  return courseCount <= MAX_CARD_CHAPTER_COUNT
}

/**
 * Counts the chapters of one course.
 *
 * @param chapters chapters returned by the existing endpoint
 * @returns number of chapters
 * @author Caspian
 * @since 0.16.0
 */
export function countChapters(chapters: readonly TeacherChapter[]): number {
  return chapters.length
}

/**
 * Loads the chapter count of every course with bounded concurrency and a per request timeout.
 *
 * <p>A failed or timed out request stores {@code null} for that course so the card renders a
 * placeholder instead of failing the whole list. The timeout cannot cancel the underlying fetch;
 * it only stops waiting for it.</p>
 *
 * @param courseIds courses whose chapter count is needed
 * @param loadChapters existing chapter endpoint
 * @param options cache, concurrency and timeout settings
 * @returns chapter count per requested course id
 * @author Caspian
 * @since 0.16.0
 */
export async function loadChapterCounts(
  courseIds: readonly number[],
  loadChapters: (courseId: number) => Promise<readonly TeacherChapter[]>,
  options: ChapterCountOptions = {},
): Promise<ChapterCounts> {
  const { cached, concurrency = 4, timeoutMs = 5000 } = options
  const counts: ChapterCounts = new Map()
  const pending: number[] = []
  for (const courseId of courseIds) {
    if (cached?.has(courseId)) {
      counts.set(courseId, cached.get(courseId) ?? null)
    } else {
      pending.push(courseId)
    }
  }
  const workers = Math.min(Math.max(concurrency, 1), pending.length)
  let cursor = 0
  async function worker(): Promise<void> {
    while (cursor < pending.length) {
      const courseId = pending[cursor]
      cursor += 1
      try {
        counts.set(courseId, countChapters(await withTimeout(loadChapters(courseId), timeoutMs)))
      } catch {
        counts.set(courseId, null)
      }
    }
  }
  await Promise.all(Array.from({ length: workers }, () => worker()))
  return counts
}

/** Rejects when the task does not settle within the ceiling, leaving the task itself running. */
function withTimeout<T>(task: Promise<T>, timeoutMs: number): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('chapter count timed out')), timeoutMs)
    task.then(
      (value) => {
        clearTimeout(timer)
        resolve(value)
      },
      (error) => {
        clearTimeout(timer)
        reject(error)
      },
    )
  })
}
