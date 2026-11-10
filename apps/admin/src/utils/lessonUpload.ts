/**
 * Local pre-checks of a lesson video before an upload session is created.
 *
 * <p>These checks only fail fast inside the browser. The backend remains the only authority: it
 * re-validates the declared size, probes the stored object and returns the authoritative
 * {@code expectedSizeBytes} that the upload must match.</p>
 *
 * @author Caspian
 * @since 0.9.0
 */

/**
 * Mirror of the server ceiling {@code lp.course.upload.max-size-bytes}, the same value guarded by
 * the V9 {@code chk_lesson_upload_size} check. Fast-fail only: the server stays authoritative, so
 * the two numbers must change together.
 */
export const MAX_VIDEO_BYTES = 2_147_483_648

/** Uploads above this size get a "slow, no resume" hint instead of a progress promise. */
export const SLOW_UPLOAD_BYTES = 524_288_000

/** Shortest accepted video, matching the backend acceptance rule. */
export const MIN_VIDEO_SECONDS = 5

/** Longest accepted video, matching the backend acceptance rule. */
export const MAX_VIDEO_SECONDS = 4 * 60 * 60

/** Size and duration of a picked file, reduced to what the checks need. */
export interface LocalVideoFile {
  name: string
  /** MIME type reported by the browser, possibly empty. */
  type: string
  size: number
}

/**
 * Formats a byte count for display.
 *
 * @param bytes size in bytes
 * @returns text such as {@code 1.5 MiB}
 * @author Caspian
 * @since 0.9.0
 */
export function formatBytes(bytes: number): string {
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB']
  let value = Number.isFinite(bytes) && bytes > 0 ? bytes : 0
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit += 1
  }
  return unit === 0 ? `${value} B` : `${value.toFixed(1)} ${units[unit]}`
}

/**
 * Tells whether an upload is large enough to warn about the missing resume support.
 *
 * @param sizeBytes size of the picked file
 * @returns true when the file is above 500 MiB
 * @author Caspian
 * @since 0.9.0
 */
export function isSlowUpload(sizeBytes: number): boolean {
  return sizeBytes > SLOW_UPLOAD_BYTES
}

/**
 * Checks a picked file against the frozen acceptance rules.
 *
 * @param file picked file
 * @param durationSec duration read from the video element, {@code null} when unreadable
 * @returns a specific rejection reason, or {@code null} when the file may be uploaded
 * @author Caspian
 * @since 0.9.0
 */
export function checkLocalVideo(
  file: LocalVideoFile,
  durationSec: number | null,
): string | null {
  const name = file.name.toLowerCase()
  const type = file.type.toLowerCase()
  if (!name.endsWith('.mp4') || (type !== '' && type !== 'video/mp4')) {
    return '仅支持 MP4 文件'
  }
  if (!Number.isFinite(file.size) || file.size <= 0) {
    return '文件为空，请重新选择'
  }
  if (file.size > MAX_VIDEO_BYTES) {
    return `文件 ${formatBytes(file.size)} 超过 2 GiB 上限`
  }
  if (durationSec === null || !Number.isFinite(durationSec) || durationSec <= 0) {
    return '无法读取视频时长，请确认文件是完整的 MP4'
  }
  if (durationSec < MIN_VIDEO_SECONDS) {
    return `视频时长 ${durationSec.toFixed(1)} 秒，少于 5 秒`
  }
  if (durationSec > MAX_VIDEO_SECONDS) {
    return `视频时长 ${formatDuration(durationSec)}，超过 4 小时`
  }
  return null
}

/**
 * Formats a duration for rejection text.
 *
 * @param durationSec duration in seconds
 * @returns text such as {@code 4.5 小时}
 * @author Caspian
 * @since 0.9.0
 */
function formatDuration(durationSec: number): string {
  return `${(durationSec / 3600).toFixed(1)} 小时`
}
