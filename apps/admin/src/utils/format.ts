/**
 * Display helpers shared by the console views.
 *
 * @author Caspian
 * @since 0.2.0
 */

/**
 * Formats a yuan amount with two decimals.
 *
 * @param amount amount in yuan, possibly missing
 * @returns text such as {@code 99.00}
 * @author Caspian
 * @since 0.2.0
 */
export function formatYuan(amount: number | null | undefined): string {
  return typeof amount === 'number' && Number.isFinite(amount) ? amount.toFixed(2) : '--'
}

/**
 * Formats a fen amount as yuan.
 *
 * @param fen amount in fen
 * @returns text such as {@code 99.00}
 * @author Caspian
 * @since 0.2.0
 */
export function formatFen(fen: number | null | undefined): string {
  return typeof fen === 'number' && Number.isFinite(fen) ? (fen / 100).toFixed(2) : '--'
}

/**
 * Formats a ratio as a percentage.
 *
 * @param ratio value between 0 and 1
 * @returns text such as {@code 62.5%}
 * @author Caspian
 * @since 0.2.0
 */
export function formatPercent(ratio: number): string {
  if (!Number.isFinite(ratio)) {
    return '--'
  }
  return `${(ratio * 100).toFixed(1)}%`
}

/**
 * Formats a duration in seconds for learning figures.
 *
 * <p>The value is always a lifetime sum, so the text only needs hours, minutes and seconds; days
 * are kept out of the way to stay readable in a table cell.</p>
 *
 * @param seconds non negative seconds, possibly missing
 * @returns text such as {@code 3 小时 12 分}, {@code 8 分 30 秒} or {@code --}
 * @author Caspian
 * @since 0.16.0
 */
export function formatDuration(seconds: number | null | undefined): string {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds < 0) {
    return '--'
  }
  const total = Math.floor(seconds)
  if (total < 60) {
    return `${total} 秒`
  }
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`
  }
  const rest = total % 60
  return rest === 0 ? `${minutes} 分` : `${minutes} 分 ${rest} 秒`
}

/**
 * Formats an ISO timestamp for table cells.
 *
 * @param value timestamp returned by the backend
 * @returns {@code YYYY-MM-DD HH:mm} in the browser timezone
 * @author Caspian
 * @since 0.2.0
 */
export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const pad = (part: number): string => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
