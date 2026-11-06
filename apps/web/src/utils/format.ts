/**
 * Display helpers shared by the course pages.
 *
 * @author Caspian
 * @since 0.2.0
 */

/**
 * Formats a price held in cents as a yuan amount.
 *
 * @param cents amount in cents; the backend stores every course price in cents
 * @returns display text such as {@code 99.00}, or {@code --} when missing
 * @author Caspian
 * @since 0.2.0
 */
export function formatCents(cents: number | null | undefined): string {
  if (typeof cents !== 'number' || !Number.isFinite(cents)) {
    return '--'
  }
  return (cents / 100).toFixed(2)
}

/**
 * Formats a yuan amount returned by the trade service.
 *
 * @param amount amount in yuan, possibly missing
 * @returns display text such as {@code 99.00}, or {@code --} when missing
 * @author Caspian
 * @since 0.2.0
 */
export function formatYuan(amount: number | null | undefined): string {
  if (typeof amount !== 'number' || !Number.isFinite(amount)) {
    return '--'
  }
  return amount.toFixed(2)
}

/**
 * Formats a percentage for the progress bar and labels.
 *
 * @param percent value between 0 and 100
 * @returns integer percentage text
 * @author Caspian
 * @since 0.2.0
 */
export function formatPercent(percent: number | null | undefined): string {
  if (typeof percent !== 'number' || !Number.isFinite(percent)) {
    return '0%'
  }
  return `${Math.max(0, Math.min(100, Math.round(percent)))}%`
}
