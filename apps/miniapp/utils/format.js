/**
 * Display helpers of the mini program.
 */

/**
 * Formats a fen amount as yuan.
 *
 * @param {number} fen amount in fen
 * @return {string} text such as 99.00
 */
function formatCents(fen) {
  if (typeof fen !== 'number' || !isFinite(fen)) {
    return '--'
  }
  return (fen / 100).toFixed(2)
}

/**
 * Formats a second count as mm:ss.
 *
 * @param {number} seconds duration in seconds
 * @return {string} text such as 12:05
 */
function formatDuration(seconds) {
  if (typeof seconds !== 'number' || !isFinite(seconds) || seconds < 0) {
    return '--'
  }
  const total = Math.floor(seconds)
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  const rest = String(total % 60).padStart(2, '0')
  return minutes + ':' + rest
}

/**
 * Formats a progress ratio as a percentage.
 *
 * @param {number} ratio value between 0 and 1
 * @return {string} text such as 62%
 */
function formatPercent(ratio) {
  if (typeof ratio !== 'number' || !isFinite(ratio)) {
    return '0%'
  }
  return Math.round(ratio * 100) + '%'
}

/**
 * Turns a rejected request into display text.
 *
 * @param {*} error rejection value of the request client
 * @param {string} fallback text used when the error carries no message
 * @return {string} display text
 */
function describeError(error, fallback) {
  if (error && error.message) {
    return error.message
  }
  return fallback || '操作失败，请稍后重试'
}

module.exports = {
  formatCents,
  formatDuration,
  formatPercent,
  describeError,
}
