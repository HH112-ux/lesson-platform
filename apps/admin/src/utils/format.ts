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
