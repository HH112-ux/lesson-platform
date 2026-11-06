/**
 * Session helpers of the mini program.
 *
 * <p>The mini program signs in silently with wx.login; there is no password screen. Pages that
 * need a session call ensureLogin and render the backend error when the channel is disabled.</p>
 */
const { request, ACCESS_KEY, REFRESH_KEY } = require('./request')

/**
 * Ensures an access token exists, signing in with wx.login when needed.
 *
 * @return {Promise<void>} resolves with a usable token
 */
function ensureLogin() {
  const token = wx.getStorageSync(ACCESS_KEY)
  if (token) {
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    wx.login({
      success(res) {
        if (!res.code) {
          reject({ code: 0, message: '微信登录失败，请稍后重试' })
          return
        }
        request('/api/v1/auth/miniapp-login', {
          method: 'POST',
          auth: false,
          data: { code: res.code },
        })
          .then((data) => {
            wx.setStorageSync(ACCESS_KEY, data.accessToken)
            wx.setStorageSync(REFRESH_KEY, data.refreshToken)
            resolve()
          })
          .catch(reject)
      },
      fail() {
        reject({ code: 0, message: '微信登录失败，请稍后重试' })
      },
    })
  })
}

/**
 * Clears the stored session.
 */
function signOut() {
  wx.removeStorageSync(ACCESS_KEY)
  wx.removeStorageSync(REFRESH_KEY)
}

module.exports = {
  ensureLogin,
  signOut,
}
