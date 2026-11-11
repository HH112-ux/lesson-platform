// Mini program entry: registers the global app instance used by every page.
const defaults = require('./config')

// config.local.js is optional and git-ignored, but it is bundled into preview and upload
// packages on purpose: it is the only way to point a real device at a LAN/HTTPS gateway.
// A separate upload gate rejects loopback and private hosts before release.
let local = {}
try {
  local = require('./config.local')
} catch (error) {
  local = {}
}

App({
  globalData: {
    // Gateway address; override per environment before release.
    apiBaseUrl: local.apiBaseUrl || defaults.apiBaseUrl,
    // Web purchase page opened in a browser, because the mini program does not take payments.
    webBaseUrl: local.webBaseUrl || defaults.webBaseUrl,
  },
})
