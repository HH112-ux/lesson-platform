import { describe, expect, it } from 'vitest'
import type { TeacherChapter } from '../api/teacher'
import {
  MAX_CARD_CHAPTER_COUNT,
  countChapters,
  loadChapterCounts,
  shouldLoadCardChapterCounts,
} from './lessonCourses'

/** Builds a chapter with the given identifier and lesson count. */
function chapter(chapterId: number, lessons = 0): TeacherChapter {
  return {
    chapterId,
    title: `Chapter ${chapterId}`,
    sort: chapterId,
    status: 1,
    lessons: Array.from({ length: lessons }, (_, index) => ({
      lessonId: chapterId * 100 + index,
      chapterId,
      title: `Lesson ${index}`,
      hasVideo: false,
      durationSec: 0,
      videoCodec: '',
      videoWidth: 0,
      videoHeight: 0,
      videoSizeBytes: 0,
      free: false,
      sort: index,
      status: 1,
    })),
  }
}

describe('shouldLoadCardChapterCounts', () => {
  it('allows the lookup up to the threshold', () => {
    expect(shouldLoadCardChapterCounts(0)).toBe(true)
    expect(shouldLoadCardChapterCounts(MAX_CARD_CHAPTER_COUNT)).toBe(true)
    expect(shouldLoadCardChapterCounts(MAX_CARD_CHAPTER_COUNT + 1)).toBe(false)
  })
})

describe('countChapters', () => {
  it('counts every chapter regardless of its lessons', () => {
    expect(countChapters([])).toBe(0)
    expect(countChapters([chapter(1), chapter(2, 3)])).toBe(2)
  })
})

describe('loadChapterCounts', () => {
  it('maps every course to its chapter count', async () => {
    const counts = await loadChapterCounts([10, 20], async (courseId) =>
      courseId === 10 ? [chapter(1), chapter(2)] : [],
    )

    expect(counts.get(10)).toBe(2)
    expect(counts.get(20)).toBe(0)
  })

  it('loads nothing when there is no course', async () => {
    let calls = 0
    const counts = await loadChapterCounts([], async () => {
      calls += 1
      return []
    })

    expect(calls).toBe(0)
    expect(counts.size).toBe(0)
  })

  it('reuses cached counts instead of calling the endpoint again', async () => {
    const requested: number[] = []
    const counts = await loadChapterCounts(
      [1, 2],
      async (courseId) => {
        requested.push(courseId)
        return [chapter(1)]
      },
      { cached: new Map([[1, 4]]) },
    )

    expect(requested).toEqual([2])
    expect(counts.get(1)).toBe(4)
    expect(counts.get(2)).toBe(1)
  })

  it('keeps the other cards when one chapter request fails', async () => {
    const counts = await loadChapterCounts([1, 2, 3], async (courseId) => {
      if (courseId === 2) {
        throw new Error('chapter request failed')
      }
      return [chapter(1)]
    })

    expect(counts.get(1)).toBe(1)
    expect(counts.get(2)).toBeNull()
    expect(counts.get(3)).toBe(1)
  })

  it('treats a slow request as unknown instead of waiting forever', async () => {
    const counts = await loadChapterCounts(
      [1],
      () => new Promise<readonly TeacherChapter[]>(() => undefined),
      { timeoutMs: 5 },
    )

    expect(counts.get(1)).toBeNull()
  })

  it('never exceeds the requested concurrency', async () => {
    let active = 0
    let peak = 0
    const counts = await loadChapterCounts(
      [1, 2, 3, 4, 5],
      async () => {
        active += 1
        peak = Math.max(peak, active)
        await new Promise((resolve) => setTimeout(resolve, 1))
        active -= 1
        return []
      },
      { concurrency: 2 },
    )

    expect(peak).toBe(2)
    expect(counts.size).toBe(5)
  })

  it('clamps a non-positive concurrency to one', async () => {
    let active = 0
    let peak = 0
    await loadChapterCounts(
      [1, 2],
      async () => {
        active += 1
        peak = Math.max(peak, active)
        await new Promise((resolve) => setTimeout(resolve, 1))
        active -= 1
        return []
      },
      { concurrency: 0 },
    )

    expect(peak).toBe(1)
  })
})
