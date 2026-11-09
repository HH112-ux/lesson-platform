# 去网页购买流程

## 1. 背景与范围

微信支付已从本平台移除，小程序不再承载任何支付能力。课程购买统一走「小程序签发一次性购买链接 → 浏览器打开 → 支付宝完成支付」。本文约定该链路的时序、接口、错误处理与本地联调方式，并记录浏览器端已落地内容与小程序端现状。

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

当前小程序端只完成了第 3 步的「复制链接」，第 2 步的 ticket 签发与二维码渲染尚未接入，见第 7 章。

## 3. 接口约定

| 方法 | 路径 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|
| POST | `/api/v1/orders/purchase-ticket` | 需登录 | `{courseId}` | `{url,qrPayload,ticket,expiresAt}` |
| POST | `/api/v1/orders/purchase-ticket/redeem` | 无需登录 | `{ticket}` | `{accessToken,refreshToken,expiresIn,courseId}` |
| GET | `/api/v1/courses/{courseId}` | 需登录 | — | 课程详情，含 `price`（分）与 `owned` |
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
| ticket 过期、已用或不存在 | 62012 / 62013 / 62014 | 「链接已过期或已使用」，提供重新获取引导 |
| ticket 归属其他账号 | 62015 | 提示 ticket 不属于当前浏览器登录账号 |
| 网络失败 | 0（前端合成） | 「网络连接失败，请检查网络后重试」 |
| 课程下架 | 62003 | 「课程已下架，暂时无法购买」 |
| 已购过 | 61002 或 `owned=true` | 「你已拥有该课程」并展示「去学习」入口 |
| 支付通道未配置 | 50205 | 「支付通道尚未配置，请联系客服」 |
| 其他非 2xx | 原样 | 展示后端 `message`，不吞错 |

## 6. 浏览器端实现

- 页面路由：`/purchase`，由 `apps/web/src/App.vue` 按 `pathname` 分发，未引入路由库。
- 登录态：`localStorage` 的 `lp.accessToken` 与 `lp.refreshToken`，读写集中在 `apps/web/src/api/client.ts`；请求统一加 `Authorization: Bearer <token>`。
- 页面实现：`apps/web/src/views/PurchaseView.vue`；错误文案与价格格式化等纯函数在 `apps/web/src/utils/purchase.ts`，由 `purchase.spec.ts` 覆盖。
- 下单与支付复用现有 `/api/v1/orders`、`/api/v1/payments/{orderNo}/pay`，不新增支付逻辑。
- 「去学习」入口指向 `/learning/my-courses`，该页面已实现（按最近学习排序，含退款失效标记）。

## 7. 小程序端现状与缺口

小程序已有微信登录（`apps/miniapp/utils/auth.js`）、课程详情（`pages/course`）、我的课程（`pages/my-courses`）与播放（`pages/player`）页面。课程页的购买入口当前只是把 `webBaseUrl + /courses/{courseId}` 复制到剪贴板，并弹窗提示到浏览器完成购买；**未调用 `POST /api/v1/orders/purchase-ticket`，也没有二维码渲染**。

| 缺口 | 说明 |
|---|---|
| ticket 签发 | 课程页应携带 access token 调用 `purchase-ticket`，用返回的 `url` / `qrPayload` 生成二维码；ticket 只存在于内存，不写入日志或持久化存储 |
| 二维码 | 需引入轻量二维码库或用 canvas 绘制，扫码与复制链接等价 |
| 过期与重取 | ticket 15 分钟过期后提供「重新获取」，不复用旧 ticket |

除上述缺口外，小程序不接入任何支付 SDK，不引入 wxpay。

## 8. 本地联调

1. 后端需配置购买链接的站点地址（如 `LP_WEB_PURCHASE_BASE_URL=http://localhost:5175`），`purchase-ticket` 返回的 `url` 与 `qrPayload` 使用该地址拼接 `/purchase?ticket=...`。
2. 前端 dev server 使用非默认端口启动，避免与后端线占用的端口冲突：`pnpm --filter lp-web dev -- --port 5175`。
3. 在浏览器直接访问 `http://localhost:5175/purchase?ticket=<ticket>` 即可走完整链路；ticket 需由 `POST /api/v1/orders/purchase-ticket` 实际签发，不能手工构造。
4. 二维码内容格式与 `url` 完全一致，扫码等价于复制链接，便于用手机与桌面浏览器分别验证。

## 9. 契约差异与已知限制

- `GET /api/v1/courses/{courseId}` 已由 `lp-course-service` 的公开接口（`CoursePublicController`）实现，返回 `price`（分）与 `owned`；小程序与浏览器端均调用该公开契约。
- `POST /api/v1/orders/purchase-ticket` 与 `redeem` 已实现，字段以实际返回为准；若字段名不同，以前端适配后端为准并回写本文。
- `payParams` 目前仅在支付凭证配置后填充；未配置时返回 50205，页面按「支付通道尚未配置」提示。
- 小程序端尚未签发 ticket 与渲染二维码，缺口见第 7 章。
