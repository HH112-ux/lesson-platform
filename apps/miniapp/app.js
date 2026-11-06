// Mini program entry: registers the global app instance used by every page.
App({
  globalData: {
    // Gateway address; override per environment before release.
    apiBaseUrl: 'http://localhost:8080',
    // Web purchase page opened in a browser, because the mini program does not take payments.
    webBaseUrl: 'http://localhost:5173',
  },
})
