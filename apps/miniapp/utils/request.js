/**
 * Gateway client of the mini program.
 *
 * <p>Every endpoint answers with {code, message, data, traceId}. The client unwraps the envelope,
 * attaches the access token, renews an expired token once and rejects with an object carrying the
 * backend code so pages can render the real error message.</p>
 */
const ACCESS_KEY = 'lp.accessToken'
const REFRESH_KEY = 'lp.refreshToken'

let refreshInFlight = null

function apiBaseUrl() {
  return getApp().globalData.apiBaseUrl
}

function rawRequest(path, method, data, token) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: apiBaseUrl() + path,
      method: method,
      data: data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success(res) {
        const body = res.data
        if (!body || typeof body.code !== 'number') {
          reject({ code: res.statusCode || 0, message: '服务暂时不可用，请稍后重试' })
          return
        }
        if (body.code !== 0) {
          reject({ code: body.code, message: body.message || '操作失败，请稍后重试' })
          return
        }
        resolve(body.data)
      },
      fail() {
        reject({ code: 0, message: '网络连接失败，请检查网络后重试' })
      },
    })
  })
}

function refreshSession() {
  if (refreshInFlight) {
    return refreshInFlight
  }
  const refreshToken = wx.getStorageSync(REFRESH_KEY)
  if (!refreshToken) {
    return Promise.resolve(false)
  }
  refreshInFlight = rawRequest('/api/v1/auth/refresh', 'POST', { refreshToken }, null)
    .then((data) => {
      wx.setStorageSync(ACCESS_KEY, data.accessToken)
      wx.setStorageSync(REFRESH_KEY, data.refreshToken)
      return true
    })
    .catch(() => false)
    .then((renewed) => {
      refreshInFlight = null
      return renewed
    })
  return refreshInFlight
}

function send(path, options, retried) {
  const token = options.auth === false ? null : wx.getStorageSync(ACCESS_KEY)
  return rawRequest(path, options.method || 'GET', options.data, token).catch((error) => {
    if (error.code === 40102 && !retried && options.auth !== false) {
      return refreshSession().then((renewed) => {
        if (renewed) {
          return send(path, options, true)
        }
        wx.removeStorageSync(ACCESS_KEY)
        wx.removeStorageSync(REFRESH_KEY)
        throw error
      })
    }
    throw error
  })
}

/**
 * Calls a gateway endpoint and resolves the envelope payload.
 *
 * @param {string} path gateway path such as /api/v1/courses
 * @param {object} options method, data and auth flag
 * @return {Promise<*>} envelope payload
 */
function request(path, options) {
  return send(path, options || {}, false)
}

module.exports = {
  request,
  refreshSession,
  ACCESS_KEY,
  REFRESH_KEY,
}
