import { describe, expect, it } from 'vitest'
import {
  MAX_VIDEO_BYTES,
  MAX_VIDEO_SECONDS,
  checkLocalVideo,
  formatBytes,
  isSlowUpload,
} from './lessonUpload'

/** Builds a picked file with the given size. */
function file(size: number, name = 'lesson.mp4', type = 'video/mp4') {
  return { name, type, size }
}

describe('checkLocalVideo', () => {
  it('accepts a short mp4 below the ceiling', () => {
    expect(checkLocalVideo(file(95_698), 6)).toBeNull()
  })

  it('accepts the boundary size and duration', () => {
    expect(checkLocalVideo(file(MAX_VIDEO_BYTES), 6)).toBeNull()
    expect(checkLocalVideo(file(1024), MAX_VIDEO_SECONDS)).toBeNull()
  })

  it('rejects a file whose extension is not mp4', () => {
    expect(checkLocalVideo(file(1024, 'lesson.mov', 'video/quicktime'), 6)).toBe('仅支持 MP4 文件')
  })

  it('rejects an mp4 name carrying another mime type', () => {
    expect(checkLocalVideo(file(1024, 'lesson.mp4', 'video/quicktime'), 6)).toBe('仅支持 MP4 文件')
  })

  it('rejects an empty file', () => {
    expect(checkLocalVideo(file(0), 6)).toBe('文件为空，请重新选择')
  })

  it('rejects a fake size one byte above the ceiling', () => {
    const reason = checkLocalVideo(file(MAX_VIDEO_BYTES + 1), 6)

    expect(reason).toBe('文件 2.0 GiB 超过 2 GiB 上限')
  })

  it('rejects a video shorter than five seconds', () => {
    expect(checkLocalVideo(file(1024), 3)).toBe('视频时长 3.0 秒，少于 5 秒')
  })

  it('rejects a video longer than four hours', () => {
    expect(checkLocalVideo(file(1024), MAX_VIDEO_SECONDS + 1)).toBe('视频时长 4.0 小时，超过 4 小时')
  })

  it('rejects an unreadable duration with an explicit reason', () => {
    expect(checkLocalVideo(file(1024), null)).toBe('无法读取视频时长，请确认文件是完整的 MP4')
    expect(checkLocalVideo(file(1024), Number.NaN)).toBe('无法读取视频时长，请确认文件是完整的 MP4')
  })
})

describe('formatBytes', () => {
  it('formats bytes, kibibytes and gibibytes', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(95_698)).toBe('93.5 KiB')
    expect(formatBytes(MAX_VIDEO_BYTES)).toBe('2.0 GiB')
  })
})

describe('isSlowUpload', () => {
  it('only flags files above 500 MiB', () => {
    expect(isSlowUpload(524_288_000)).toBe(false)
    expect(isSlowUpload(524_288_001)).toBe(true)
  })
})
