const app = getApp()

Page({
  data: {
    title: '课程平台',
    apiBaseUrl: '',
  },

  onLoad() {
    this.setData({ apiBaseUrl: app.globalData.apiBaseUrl })
  },
})
