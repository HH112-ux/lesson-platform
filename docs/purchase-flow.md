# 去网页购买流程

## 1. 背景与范围

微信支付已从本平台移除，小程序不再承载任何支付能力。课程购买统一走「小程序签发一次性购买链接 → 浏览器打开 → 支付宝完成支付」。本文约定该链路的时序、接口、错误处理与本地联调方式，并记录浏览器端已落地内容与小程序端待实施内容。

工程侧接口命名与错误码遵循 [engineering-plan.md](./engineering-plan.md) 第 5 章与 AGENTS.md 11.4。

## 2. 整体时序

1. 学员在小程序课程页点击「去网页购买」。
2. 小程序携带现有 access token 调用 `POST /api/v1/orders/purchase-ticket`，拿到 `url`、`qrPayload` 与 `expiresAt`。
3. 小程序把 `qrPayload` 渲染成二维码，同时提供「复制链接」按钮与「请在浏览器中打开」提示。
4. 学员扫码或复制链接到系统浏览器，打开 `https://<web-host>/purchase?ticket=<ticket>`。
5. 浏览器调用 `POST /api/v1/orders/purchase-ticket/redeem` 兑换 token，兑换成功后立即从地址栏移除 ticket。
6. 浏览器用新 token 拉取 `GET /api/v1/courses/{courseId}`，展示课程名与价格。
7. 学员点击「支付宝支付」，浏览器依次调用 `POST /api/v1/orders` 与 `POST /api/v1/payments/{orderNo}/pay`，跳转支付宝收银台。
8. 支付宝异步回调由后端处理；学员回到浏览器刷新即可看到学习入口。

## 3. 接口约定

| 方法 | 路径 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/orders/purchase-ticket` | 需登录 | `{courseId}` | `{url,qrPayload,ticket,expiresAt}` |
| POST | `/api/v1/orders/purchase-ticket/redeem` | 无需登录 | `{ticket}` | `{accessToken,refreshToken,expiresIn,courseId}` |
| GET | `/api/v1/courses/{courseId}` | 需登录 | — | 课程详情，含 `price`、`entitled` |
| POST | `/api/v1/orders` | 需登录 | `{courseId,idempotencyKey}` | `{orderNo,status,payAmount,...}` |
| POST | `/api/v1/payments/{orderNo}/pay` | 需登录 | — | `{payNo,channel,payParams}` |

`redeem` 返回的 token 与正常登录一致，直接写入浏览器会话；浏览器来源经网关固定映射为 `web`，下单渠道因此固定为支付宝，客户端不传渠道参数。

## 4. Ticket 规则

- 有效期 15 分钟，一次性使用；兑换成功即失效，重复兑换返回非 2xx。
- Ticket 为不透明随机串，URL 中不包含明文 `userId`、手机号或课程价格。
- 前端不把 ticket 写入日志、埋点或任何持久化存储；兑换成功后用 `history.replaceState` 从地址栏移除 `ticket` 参数，避免被复制或留在浏览器历史。

## 5. 错误处理

| 场景 | 后端错误码 | 页面表现 |
|---|---|---|
| 无 ticket 参数 | — | 「链接无效，请回到小程序重新获取」 |
| ticket 过期、已用或不存在 | 40101 / 40102 | 「链接已过期或已使用」，提供重新获取引导 |
| 网络失败 | 0（前端合成） | 「网络连接失败，请检查网络后重试」 |
| 课程下架 | 61001 / 62003 | 「课程已下架，暂时无法购买」 |
| 已购过 | 61002 或 `entitled=true` | 「你已拥有该课程」并展示「去学习」入口 |
| 支付通道未配置 | 50205 | 「支付通道尚未配置，请联系客服」 |
| 其他非 2xx | 原样 | 展示后端 `message`，不吞错 |

## 6. 浏览器端实现

- 页面路由：`/purchase`，由 `apps/web/src/App.vue` 按 `pathname` 分发，未引入路由库。
- 登录态：`localStorage` 的 `lp.accessToken` 与 `lp.refreshToken`，读写集中在 `apps/web/src/api/client.ts`；请求统一加 `Authorization: Bearer <token>`。
- 页面实现：`apps/web/src/views/PurchaseView.vue`；错误文案与价格格式化等纯函数在 `apps/web/src/utils/purchase.ts`，由 `purchase.spec.ts` 覆盖。
- 下单与支付复用现有 `/api/v1/orders`、`/api/v1/payments/{orderNo}/pay`，不新增支付逻辑。
- 「去学习」入口指向 `/learning/my-courses`，该页面尚未落地，属后续批次。

## 7. 小程序端方案（未实施）

当前仓库的小程序仅有一个骨架页，没有课程详情页、购买入口、登录态与二维码依赖，完整改造会超过 300 行且需要引入二维码库，因此本轮只给出方案：

| 文件 | 变更 |
|---|---|
| `apps/miniapp/pages/course/course.js` | 新增课程详情页：拉取课程、维护购买面板状态、申请 ticket、复制链接、错误与重新获取 |
| `apps/miniapp/pages/course/course.wxml` | 课程信息、「去网页购买」按钮、二维码面板、复制链接与提示文案 |
| `apps/miniapp/pages/course/course.wxss` | 沿用现有配色与卡片样式 |
| `apps/miniapp/pages/course/course.json` | 页面标题配置 |
| `apps/miniapp/utils/request.js` | 封装 `wx.request`、token 注入与统一错误码解析 |
| `apps/miniapp/utils/qrcode.js` | 轻量二维码编码实现或在 `package.json` 引入轻量库，用 canvas 绘制 |
| `apps/miniapp/app.json` | 注册新页面 |

交互要求：未登录时提示先登录；接口失败展示可重试提示；ticket 过期时提供「重新获取」；不接入任何支付 SDK，不引入 wxpay。

## 8. 本地联调

1. 后端需配置购买链接的站点地址（如 `LP_WEB_PURCHASE_BASE_URL=http://localhost:5175`），`purchase-ticket` 返回的 `url` 与 `qrPayload` 使用该地址拼接 `/purchase?ticket=...`。
2. 前端 dev server 使用非默认端口启动，避免与后端线占用的端口冲突：`pnpm --filter lp-web dev -- --port 5175`。
3. 在浏览器直接访问 `http://localhost:5175/purchase?ticket=<ticket>` 即可走完整链路；ticket 需由 `POST /api/v1/orders/purchase-ticket` 实际签发，不能手工构造。
4. 二维码内容格式与 `url` 完全一致，扫码等价于复制链接，便于用手机与桌面浏览器分别验证。

## 9. 契约差异与已知限制

- `GET /api/v1/courses/{courseId}` 已在文档定义，但当前代码仅实现内部接口 `/internal/v1/courses/{id}`，浏览器端按文档契约调用，公共接口落地前该请求会失败。
- `POST /api/v1/orders/purchase-ticket` 与 `redeem` 由后端线并行实现，字段以实际返回为准；若字段名不同，以前端适配后端为准并回写本文。
- `payParams` 目前仅在支付凭证配置后填充；未配置时返回 50205，页面按「支付通道尚未配置」提示。
- 小程序端未实施，原因见第 7 章。
