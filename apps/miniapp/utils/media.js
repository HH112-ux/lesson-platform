/**
 * Helpers that turn media paths returned by the API into loadable URLs.
 *
 * The API returns site-relative paths such as `/lp-media/covers/1.png`.
 * The mini program `<image>` component treats them as package paths, so they
 * must be resolved against the API origin before rendering.
 */

const ABSOLUTE_URL = /^https?:\/\//i

/**
 * Resolves a media path into an absolute URL.
 *
 * Absolute `http(s)` URLs pass through unchanged. Site-relative paths are
 * prefixed with the configured base URL. Other values are returned as-is, and
 * a missing base URL is reported instead of being silently dropped.
 *
 * @param {*} path media path returned by the API
 * @param {*} baseUrl site or media base such as `https://example.com`
 * @param {Function} [warn] reporter used when the base URL is missing
 * @return {string} absolute URL, original value, or an empty string for empty input
 */
function resolveMediaUrl(path, baseUrl, warn) {
  if (typeof path !== 'string') {
    return ''
  }
  const value = path.trim()
  if (!value) {
    return ''
  }
  if (ABSOLUTE_URL.test(value)) {
    return value
  }
  if (value.charAt(0) !== '/') {
    return value
  }
  const base = typeof baseUrl === 'string' ? baseUrl.trim().replace(/\/+$/, '') : ''
  if (!base) {
    const report = typeof warn === 'function' ? warn : console.warn
    report('media base url is missing, keep original path: ' + value)
    return value
  }
  return base + value
}

module.exports = {
  resolveMediaUrl,
}
