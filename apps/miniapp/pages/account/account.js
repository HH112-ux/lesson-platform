/**
 * Account cancellation page of the mini program.
 *
 * <p>The page renders the state returned by the auth service and never decides on its own whether
 * the account may be used; the remaining seconds come from the server clock.</p>
 */
const { request } = require('../../utils/request')
const { ensureLogin } = require('../../utils/auth')
const { describeError } = require('../../utils/format')

const PATH = '/api/v1/auth/account-cancellation'
const MESSAGES = {
  67001: '账号已注销，无法继续使用',
  67003: '存在进行中的订单或退款，暂时无法注销',
  67004: '当前没有进行中的注销申请',
  67005: '注销冷静期中，暂不可购买',
}

/** Translates the codes this page expects; backend messages are English and carry counts. */
function describeCancellationError(error, fallback) {
  return (error && MESSAGES[error.code]) || describeError(error, fallback)
}

/** Formats a second count as days plus hh:mm:ss. */
function formatCountdown(total) {
  const pad = (value) => String(Math.floor(value)).padStart(2, '0')
  const clock =
    pad((total % 86400) / 3600) + ':' + pad((total % 3600) / 60) + ':' + pad(total % 60)
  const days = Math.floor(total / 86400)
  return days > 0 ? days + ' 天 ' + clock : clock
}

Page({
  data: {
    status: null,
    countdown: '',
    loading: true,
    errorText: '',
    submitting: false,
  },
  onLoad() {
    this.load()
  },
  onUnload() {
    this.stopTimer()
  },
  load() {
    this.setData({ loading: true, errorText: '' })
    return ensureLogin()
      .then(() => request(PATH))
      .then((view) => {
        const status = typeof view.status === 'number' ? view.status : null
        this.setData({ loading: false, status })
        if (status === 0) {
          this.startTimer(view.remainingSeconds)
        } else {
          this.stopTimer()
        }
      })
      .catch((error) => {
        this.stopTimer()
        this.setData({
          loading: false,
          status: null,
          errorText: describeCancellationError(error, '账号状态读取失败'),
        })
      })
  },
  startTimer(seconds) {
    this.stopTimer()
    let left = Math.max(0, Math.floor(seconds))
    this.setData({ countdown: formatCountdown(left) })
    this.timer = setInterval(() => {
      left = Math.max(0, left - 1)
      this.setData({ countdown: formatCountdown(left) })
      if (left === 0) {
        this.stopTimer()
        this.load()
      }
    }, 1000)
  },
  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },
  askApply() {
    this.confirmThen('apply', '确认申请注销账号', '提交后进入 7 天冷静期，期间不能下单或支付；到期后账号将被删除且无法恢复。')
  },
  askWithdraw() {
    this.confirmThen('withdraw', '确认撤销注销申请', '撤销后账号恢复正常使用，本次冷静期倒计时作废。')
  },
  confirmThen(action, title, content) {
    wx.showModal({
      title,
      content,
      success: (res) => {
        if (res.confirm) {
          this.run(action)
        }
      },
    })
  },
  run(action) {
    if (this.data.submitting) {
      return
    }
    this.setData({ submitting: true })
    const call =
      action === 'apply' ? request(PATH, { method: 'POST' }) : request(PATH, { method: 'DELETE' })
    call
      .then(() => {
        this.setData({ submitting: false })
        return this.load()
      })
      .catch((error) => {
        this.setData({ submitting: false })
        wx.showModal({
          title: '操作失败',
          content: describeCancellationError(
            error,
            action === 'apply' ? '注销申请提交失败' : '撤销失败'
          ),
          showCancel: false,
        })
      })
  },
})
