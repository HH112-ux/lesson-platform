const { request } = require('../../utils/request')
const { formatCents, formatDuration, describeError } = require('../../utils/format')
const { resolveMediaUrl } = require('../../utils/media')

Page({
  data: {
    courseId: 0,
    course: null,
    chapters: [],
    owned: false,
    priceText: '--',
    loading: true,
    errorText: '',
  },

  onLoad(options) {
    const courseId = Number(options.id)
    if (!courseId) {
      this.setData({ loading: false, errorText: '课程不存在' })
      return
    }
    this.setData({ courseId: courseId })
    this.load()
  },

  onShow() {
    if (this.data.courseId && !this.data.loading) {
      this.load()
    }
  },

  load() {
    this.setData({ loading: true, errorText: '' })
    Promise.all([
      request('/api/v1/courses/' + this.data.courseId),
      request('/api/v1/courses/' + this.data.courseId + '/chapters'),
    ])
      .then((results) => {
        const course = Object.assign({}, results[0], {
          coverUrl: resolveMediaUrl(results[0].coverUrl, getApp().globalData.apiBaseUrl),
        })
        const outline = results[1]
        const chapters = (outline.chapters || []).map((chapter) =>
          Object.assign({}, chapter, {
            lessons: (chapter.lessons || []).map((lesson) =>
              Object.assign({}, lesson, {
                durationText: formatDuration(lesson.durationSec),
              }),
            ),
          }),
        )
        this.setData({
          course: course,
          chapters: chapters,
          owned: Boolean(outline.owned || course.owned),
          priceText: course.price === 0 ? '免费' : '¥' + formatCents(course.price),
          loading: false,
        })
      })
      .catch((error) => {
        this.setData({ loading: false, errorText: describeError(error, '课程详情加载失败') })
      })
  },

  copyPurchaseLink() {
    const url = getApp().globalData.webBaseUrl + '/courses/' + this.data.courseId
    wx.setClipboardData({
      data: url,
      success() {
        wx.showModal({
          title: '已复制购买链接',
          content: '小程序内暂不支持支付，请在浏览器打开该链接完成购买。购买后回到本页下拉刷新即可学习。',
          showCancel: false,
          confirmText: '我知道了',
        })
      },
    })
  },

  openMyCourses() {
    wx.navigateTo({ url: '/pages/my-courses/my-courses' })
  },

  tapLesson(event) {
    const lesson = event.currentTarget.dataset
    const playable = this.data.owned || (lesson.free && lesson.playable !== false)
    if (!playable) {
      wx.showToast({ title: '购买后可学习', icon: 'none' })
      return
    }
    wx.navigateTo({
      url:
        '/pages/player/player?lessonId=' +
        lesson.id +
        '&courseId=' +
        this.data.courseId,
    })
  },

  retry() {
    this.load()
  },
})
