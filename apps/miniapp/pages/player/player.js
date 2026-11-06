const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')
const { describeError } = require('../../utils/format')

const REPORT_INTERVAL_MS = 15000

// Matches ErrorCode.LEARN_NOT_ENTITLED; a refunded course answers with it because the
// entitlement row is revoked, so the page explains the refund instead of the raw error.
const NOT_ENTITLED_CODE = 40302

/**
 * Turns a play token rejection into display text.
 *
 * @param {*} error rejection value of the request client
 * @param {string} fallback text used for other failures
 * @return {string} display text
 */
function describePlayError(error, fallback) {
  if (error && error.code === NOT_ENTITLED_CODE) {
    return '该课程已退款，学习权限已收回'
  }
  return describeError(error, fallback)
}

Page({
  data: {
    lessonId: 0,
    courseId: 0,
    playUrl: '',
    initialTime: 0,
    loading: true,
    errorText: '',
  },

  onLoad(options) {
    this.lastReportAt = 0
    this.pendingPosition = 0
    this.tokenRetried = false
    const lessonId = Number(options.lessonId)
    const courseId = Number(options.courseId)
    this.setData({ lessonId: lessonId, courseId: courseId })
    this.load()
  },

  onHide() {
    this.flush(true)
  },

  onUnload() {
    this.flush(true)
  },

  load() {
    this.setData({ loading: true, errorText: '' })
    ensureLogin()
      .then(() => {
        const token = request('/api/v1/lessons/' + this.data.lessonId + '/play-token')
        const progress = this.data.courseId
          ? request('/api/v1/learning/progress?courseId=' + this.data.courseId)
          : Promise.resolve({ lessons: [] })
        return Promise.all([token, progress])
      })
      .then((results) => {
        const play = results[0]
        const lessons = (results[1] && results[1].lessons) || []
        const current = lessons.filter((item) => item.lessonId === this.data.lessonId)[0]
        this.setData({
          playUrl: play.playUrl,
          initialTime: current ? Math.max(0, current.maxPositionSeconds || 0) : 0,
          loading: false,
        })
      })
      .catch((error) => {
        this.setData({ loading: false, errorText: describePlayError(error, '课时加载失败') })
      })
  },

  onTimeUpdate(event) {
    this.pendingPosition = Math.floor(event.detail.currentTime || 0)
    if (Date.now() - this.lastReportAt >= REPORT_INTERVAL_MS) {
      this.flush(false)
    }
  },

  onPause() {
    this.flush(true)
  },

  onEnded() {
    this.flush(true)
  },

  onVideoError() {
    if (this.tokenRetried) {
      this.setData({ errorText: '视频播放失败，请稍后重试' })
      return
    }
    this.tokenRetried = true
    request('/api/v1/lessons/' + this.data.lessonId + '/play-token')
      .then((play) => {
        this.setData({ playUrl: play.playUrl, initialTime: this.pendingPosition })
      })
      .catch((error) => {
        this.setData({ errorText: describePlayError(error, '视频播放失败，请稍后重试') })
      })
  },

  flush(force) {
    if (!this.pendingPosition) {
      return
    }
    if (!force && Date.now() - this.lastReportAt < REPORT_INTERVAL_MS) {
      return
    }
    this.lastReportAt = Date.now()
    request('/api/v1/learning/progress', {
      method: 'POST',
      data: { lessonId: this.data.lessonId, positionSeconds: this.pendingPosition },
    }).catch(() => {
      // Progress reporting is best effort; playback must not be interrupted.
    })
  },

  retry() {
    this.load()
  },
})
