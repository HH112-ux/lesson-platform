const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')
const { formatPercent, describeError } = require('../../utils/format')

Page({
  data: {
    records: [],
    loading: true,
    errorText: '',
  },

  onLoad() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().then(() => {
      wx.stopPullDownRefresh()
    })
  },

  load() {
    this.setData({ loading: true, errorText: '' })
    return ensureLogin()
      .then(() => request('/api/v1/learning/my-courses?pageNum=1&pageSize=20'))
      .then((page) => {
        const records = (page.records || []).map((course) => {
          const refunded = course.refunded === true
          const progressText = formatPercent(course.progressPercent)
          return Object.assign({}, course, {
            refunded: refunded,
            progressText: progressText,
            progressWidth: Math.max(0, Math.min(100, (course.progressPercent || 0) * 100)) + '%',
            hintText: refunded ? '已退款，学习权限已收回' : '已学 ' + progressText,
            actionText: refunded ? '已退款' : course.lastLessonId ? '继续学习' : '去学习',
          })
        })
        this.setData({ records: records, loading: false })
      })
      .catch((error) => {
        this.setData({ loading: false, errorText: describeError(error, '我的课程加载失败') })
      })
  },

  openCourse(event) {
    wx.navigateTo({
      url: '/pages/course/course?id=' + event.currentTarget.dataset.id,
    })
  },

  continueLearning(event) {
    const dataset = event.currentTarget.dataset
    // A refunded course keeps its progress but no longer opens the player.
    if (dataset.refunded === true || dataset.refunded === 'true') {
      this.openCourse(event)
      return
    }
    if (dataset.lesson) {
      wx.navigateTo({
        url: '/pages/player/player?lessonId=' + dataset.lesson + '&courseId=' + dataset.id,
      })
      return
    }
    this.openCourse(event)
  },
})
