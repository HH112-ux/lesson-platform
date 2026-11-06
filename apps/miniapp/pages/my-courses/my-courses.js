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
        const records = (page.records || []).map((course) =>
          Object.assign({}, course, {
            progressText: formatPercent(course.progressPercent),
            progressWidth: Math.max(0, Math.min(100, (course.progressPercent || 0) * 100)) + '%',
          }),
        )
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
    if (dataset.lesson) {
      wx.navigateTo({
        url: '/pages/player/player?lessonId=' + dataset.lesson + '&courseId=' + dataset.id,
      })
      return
    }
    this.openCourse(event)
  },
})
