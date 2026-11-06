const { request } = require('../../utils/request')
const { formatCents, describeError } = require('../../utils/format')

const PAGE_SIZE = 10

Page({
  data: {
    categories: [],
    activeCategoryId: 0,
    courses: [],
    page: 1,
    total: 0,
    loading: false,
    errorText: '',
  },

  onLoad() {
    this.loadCategories()
    this.loadCourses(true)
  },

  onPullDownRefresh() {
    Promise.all([this.loadCategories(), this.loadCourses(true)]).then(() => {
      wx.stopPullDownRefresh()
    })
  },

  onReachBottom() {
    if (this.data.courses.length < this.data.total && !this.data.loading) {
      this.loadCourses(false)
    }
  },

  loadCategories() {
    return request('/api/v1/categories')
      .then((items) => {
        this.setData({ categories: items || [] })
      })
      .catch(() => {
        // The catalog still works without the filter row; the list error is shown separately.
      })
  },

  loadCourses(reset) {
    const page = reset ? 1 : this.data.page + 1
    this.setData({ loading: true, errorText: '' })
    const params = ['page=' + page, 'size=' + PAGE_SIZE]
    if (this.data.activeCategoryId) {
      params.push('category=' + this.data.activeCategoryId)
    }
    return request('/api/v1/courses?' + params.join('&'))
      .then((result) => {
        const items = (result.items || []).map((course) =>
          Object.assign({}, course, {
            priceText: course.price === 0 ? '免费' : '¥' + formatCents(course.price),
          }),
        )
        this.setData({
          courses: reset ? items : this.data.courses.concat(items),
          total: result.total || 0,
          page: page,
          loading: false,
        })
      })
      .catch((error) => {
        this.setData({
          loading: false,
          errorText: describeError(error, '课程列表加载失败'),
        })
      })
  },

  tapCategory(event) {
    const categoryId = Number(event.currentTarget.dataset.id)
    if (categoryId === this.data.activeCategoryId) {
      return
    }
    this.setData({ activeCategoryId: categoryId })
    this.loadCourses(true)
  },

  tapCourse(event) {
    wx.navigateTo({
      url: '/pages/course/course?id=' + event.currentTarget.dataset.id,
    })
  },

  retry() {
    this.loadCourses(true)
  },

  goMyCourses() {
    wx.navigateTo({ url: '/pages/my-courses/my-courses' })
  },
})
