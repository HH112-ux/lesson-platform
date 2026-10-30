# my-lesson API 接口文档

> 版本：1.0.0 ｜ 最后更新：2026-10-27 ｜ 在线文档：各服务 Knife4j（`/doc.html`）

## 1. 通用约定

### 1.1 请求地址

所有接口经网关统一入口访问，服务按路径前缀分发：

| 网关前缀 | 目标服务 | 端口 |
|---|---|---|
| （网关根） | ml-gateway | 24101 |
| `/user-server/**` | ml-user | 24102 |
| `/course-server/**` | ml-course | 24103 |
| `/sale-server/**` | ml-sale | 24104 |
| `/order-server/**` | ml-order | 24105 |

接口统一前缀 `api/v1/`，示例：

```
GET http://localhost:24101/course-server/api/v1/course/page
POST http://localhost:24101/user-server/api/v1/user/loginByAccount
```

### 1.2 鉴权（Token）

- 登录成功后返回 `token`（UUID），前端存入 `localStorage`。
- 后续请求在**请求头 `token`** 携带该值（网关 `TokenGlobalFilter` 校验）。
- Token 有效期 30 分钟，每次请求自动续期 30 分钟；Redis 中不存在即视为过期。
- 网关白名单（Nacos 配置 `token.white_list`）内的请求免鉴权。
- 未携带/失效 Token 时网关直接返回：

```json
{ "code": 6000, "message": "登录过期", "coderMessage": "请求中未携带Token令牌", "data": null }
```

### 1.3 统一响应格式

所有接口由 `ResultAdvice` 统一包装为 `Result<T>`（`org.example.result.Result`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| code | Integer | 响应代码，1000 成功 |
| message | String | 面向用户的响应描述 |
| coderMessage | String | 面向开发者的响应描述（异常时多为具体原因） |
| data | T | 响应数据，无数据时 null |

成功示例：

```json
{ "code": 1000, "message": "请求成功", "coderMessage": "请求成功", "data": { ... } }
```

> 注意：Controller 方法返回值直接写业务类型（如 `User`、`boolean`、`List`），框架自动包装；返回类型已经是 `Result` 的方法不再二次包装。字符串返回会被 JSON 序列化后包装，避免转换异常。

### 1.4 分页参数

分页接口统一接收 `PageDTO` 子类（JSON Body，GET 请求亦用 body 传参）：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| pageNum | Integer | 是 | 当前页（>=0） |
| pageSize | Integer | 是 | 每页条数（>=0） |
| + 业务筛选字段 | 各 DTO 定义 | 否 | 如 username/nickname/phone 等 |

分页响应统一为 `PageVO<T>`：

| 字段 | 类型 | 说明 |
|---|---|---|
| pageNum | Long | 当前页 |
| pageSize | Integer | 每页条数 |
| totalRow | Long | 总记录数 |
| totalPage | Integer | 总页数 |
| records | List\<T\> | 当前页数据 |

### 1.5 错误码（ResultCode）

| 分类 | code | message | 说明 |
|---|---|---|---|
| 成功 | 1000 | 请求成功 | — |
| 服务器错误 | 2000 | 服务器异常 | 通用异常 |
| | 2001 | 数据操作失败 | MySQL 操作失败 |
| | 2002 | 文件操作失败 | MinIO 操作失败 |
| 请求参数错误 | 3000 | 请求参数错误 | 参数校验失败等 |
| | 3001 | 账号密码错误 | 登录密码错误 |
| | 3002 | 身份证号错误 | — |
| | 3003 | 旧密码错误 | — |
| | 3004 | 验证码错误 | — |
| | 3005 | 时间设定错误 | — |
| 记录重复 | 4000 | 记录重复 | 通用重复 |
| | 4001 | 登录账号重复 | — |
| | 4002 | 身份证号重复 | — |
| | 4003 | 手机号码重复 | — |
| | 4004 | 电子邮箱重复 | — |
| | 4005 | 标题重复 | — |
| | 4006 | 兑换口令重复 | — |
| | 4007 | 秒杀商品重复 | — |
| | 4008 | 购物车记录重复 | — |
| | 4009 | 订单明细重复 | — |
| | 4010 | 秒杀活动重复 | — |
| 记录不存在 | 5000 | 记录不存在 | 通用 |
| | 5001 | 用户不存在 | 5002 角色 / 5003 菜单 |
| | 5004 | 课程类别不存在 | 5005 课程 / 5006 季次 / 5007 集次 |
| | 5008 | 评论不存在 | 5009 举报 / 5010 收藏 |
| | 5011 | 通知不存在 | 5012 横幅 / 5013 新闻 |
| | 5014 | 秒杀活动不存在 | 5015 优惠卷 / 5016 购物车 |
| | 5017 | 订单不存在 | 5018 手机号码 / 5019 秒杀明细 / 5020 订单明细 |
| 远程调用 | 6000 | 远程调用失败 | OpenFeign 调用失败 |
| 友好提示 | 7000 | 活动未开始 | 秒杀未开始 |
| | 7001 | 活动已结束 | 秒杀已结束 |

> 网关"登录过期"亦复用 code 6000（message="登录过期"），与 Feign 错误码同号但语义不同，前端需注意区分来源。

## 2. ml-user 用户服务（40 个接口）

接口前缀：`api/v1`，在线文档：`http://localhost:24102/doc.html`

### 2.1 用户（UserController）

| 方法 | 路径 | 说明 | 请求参数 | 返回 |
|---|---|---|---|---|
| POST | `/user` `save` | 注册/保存用户 | Body：UserInsertDTO | boolean |
| DELETE | `/user` `remove/{id}` | 根据主键删除用户 | Path：id | boolean |
| PUT | `/user` `update` | 更新用户 | Body：User | boolean |
| GET | `/user` `list` | 查询所有用户 | — | List\<User\> |
| GET | `/user` `getInfo/{id}` | 根据主键获取用户 | Path：id | User |
| GET | `/user` `page` | 分页查询用户 | Body：UserPageDTO（pageNum/pageSize/username/nickname/phone） | PageVO\<User\> |
| PUT | `/user` `updatePassword` | 修改密码 | Body：UserUpdatePasswordDTO | boolean |
| POST | `/user` `uploadAvatar/{id}` | 上传用户头像 | Path：id + MultipartFile（avatarFile） | Result\<String\> 文件名 |
| POST | `/user` `loginByAccount` | 账号密码登录 | Body：LoginByAccountDTO | LoginVO |
| PUT | `/user` `info` | 修改昵称和个性签名 | Body：UpdateInfoDTO | boolean |
| GET | `/user` `getPhoneCode` | 获取手机验证码 | Query：phone | Result\<String\> 验证码 |
| PUT | `/user` `mobile` | 绑定手机号 | Body：BindMobileDTO | boolean |

**loginByAccount 请求体（LoginByAccountDTO）**

| 参数 | 类型 | 校验 | 说明 |
|---|---|---|---|
| username | String | 必填，`^[a-zA-Z0-9]{4,20}$` | 登录账号 |
| password | String | 必填，`^[a-zA-Z0-9]{4,20}$` | 登录密码（明文传输，BCrypt 比对） |

**loginByAccount 响应（LoginVO）**

```json
{
  "user": { "id": 1, "username": "test01", "nickname": "...", "...": "..." },
  "token": "6f9e...-uuid",
  "roleTitles": ["管理员"],
  "menus": [ { "id": 1, "title": "首页", "url": "/main", "...": "..." } ]
}
```

**save 请求体（UserInsertDTO）**

| 参数 | 类型 | 校验 | 说明 |
|---|---|---|---|
| username | String | 必填，4-20 位字母数字 | 账号 |
| password | String | 必填，4-20 位字母数字 | 密码 |
| realname | String | 必填，2-6 个中文 | 真实姓名 |
| idcard | String | 必填，18 位身份证格式 | 身份证号 |
| phone | String | 必填，大陆手机号 | 手机号 |
| email | String | 必填，邮箱格式 | 邮箱 |
| info | String | 选填，≤170 字符 | 描述 |

### 2.2 角色（RoleController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/role` `save` | 保存角色 | Body：RoleInsertDTO | boolean |
| DELETE | `/role` `remove/{id}` | 删除角色 | Path：id | boolean |
| PUT | `/role` `update` | 更新角色 | Body：Role | boolean |
| GET | `/role` `list` | 查询所有角色 | — | List\<Role\> |
| GET | `/role` `getInfo/{id}` | 根据主键获取角色 | Path：id | Role |
| GET | `/role` `page` | 分页查询角色 | Body：RolePageDTO | PageVO\<Role\> |
| GET | `/role` `listRoleIdsByUserId/{userId}` | 查询用户角色 ID 列表 | Path：userId | List\<Long\> |
| PUT | `/role` `updateRolesByUserId` | 修改用户角色 | Query：userId + Query：roleIds（数组） | boolean |

### 2.3 菜单（MenuController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/menu` `save` | 保存菜单 | Body：MenuInsertDTO | boolean |
| DELETE | `/menu` `remove/{id}` | 删除菜单 | Path：id | boolean |
| PUT | `/menu` `update` | 更新菜单 | Body：Menu | boolean |
| GET | `/menu` `list` | 查询所有菜单 | — | List\<Menu\> |
| GET | `/menu` `getInfo/{id}` | 根据主键获取菜单 | Path：id | Menu |
| GET | `/menu` `page` | 分页查询菜单 | Body：MenuPageDTO | PageVO\<Menu\> |
| GET | `/menu` `listRoleMenuIds/{roleId}` | 根据角色 ID 查询菜单 ID 列表 | Path：roleId | List\<Long\> |
| PUT | `/menu` `updateRoleMenu` | 根据角色 ID 更新菜单 ID 列表 | Query：roleId + Query：menuIds（数组） | boolean |

### 2.4 关系表（RoleMenuController / UserRoleController）

标准 CRUD（save/remove/update/list/getInfo/page），路径分别为 `api/v1/roleMenu`、`api/v1/userRole`，请求/响应为 `RoleMenu`、`UserRole` 实体。

## 3. ml-course 课程服务（49 个接口）

接口前缀：`api/v1`，在线文档：`http://localhost:24103/doc.html`

### 3.1 课程类别（CategoryController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/category` `save` | 保存类别 | Body：CategoryInsertDTO | boolean |
| DELETE | `/category` `remove/{id}` | 删除类别 | Path：id | boolean |
| PUT | `/category` `update` | 更新类别 | Body：Category | boolean |
| GET | `/category` `list` | 查询所有类别 | — | List\<Category\> |
| GET | `/category` `getInfo/{id}` | 根据主键获取类别 | Path：id | Category |
| GET | `/category` `page` | 分页查询类别 | Body：CategoryPageDTO | PageVO\<Category\> |
| GET | `/category` `simpleList` | 获取课程类别列表（C 端导航） | — | List\<CategorySimpleListVO\> |

### 3.2 课程（CourseController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/course` `save` | 保存课程 | Body：CourseInsertDTO | boolean |
| DELETE | `/course` `remove/{id}` | 删除课程 | Path：id | boolean |
| PUT | `/course` `update` | 更新课程 | Body：Course | boolean |
| GET | `/course` `list` | 查询所有课程 | — | List\<Course\> |
| GET | `/course` `getInfo/{id}` | 根据主键获取课程 | Path：id | Course |
| GET | `/course` `page` | 分页查询课程 | Body：CoursePageDTO | PageVO\<Course\> |
| POST | `/course` `uploadCover/{id}` | 上传课程封面 | Path：id + MultipartFile（coverFile） | Result\<String\> |
| POST | `/course` `uploadSummary/{id}` | 上传课程摘要图 | Path：id + MultipartFile（summaryFile） | Result\<String\> |
| GET | `/course` `recommend/{courseId}` | 推荐课程（相似课程） | Path：courseId | Result\<List\<Course\>\> |

### 3.3 季次（SeasonController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/season` `save` | 保存季次 | Body：SeasonInsertDTO | boolean |
| DELETE | `/season` `remove/{id}` | 删除季次 | Path：id | boolean |
| PUT | `/season` `update` | 更新季次 | Body：Season | boolean |
| GET | `/season` `list/{courseId}` | 查询课程下所有季次 | Path：courseId | List\<Season\> |
| GET | `/season` `getInfo/{id}` | 根据主键获取季次 | Path：id | Season |
| GET | `/season` `page` | 分页查询季次 | Body：SeasonPageDTO | PageVO\<Season\> |

### 3.4 集次（EpisodeController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/episode` `save` | 保存集次 | Body：EpisodeInsertDTO | boolean |
| DELETE | `/episode` `remove/{id}` | 删除集次 | Path：id | boolean |
| PUT | `/episode` `update` | 更新集次 | Body：Episode | boolean |
| GET | `/episode` `list` | 查询所有集次 | — | List\<Episode\> |
| GET | `/episode` `getInfo/{id}` | 根据主键获取集次 | Path：id | Episode |
| GET | `/episode` `page` | 分页查询集次 | Body：EpisodePageDTO | PageVO\<Episode\> |
| POST | `/episode` `uploadVideoCover/{id}` | 上传集次视频封面 | Path：id + MultipartFile（videoCoverFile） | Result |
| POST | `/episode` `uploadVideo/{id}` | 上传集次视频 | Path：id + MultipartFile（videoFile） | Result |

### 3.5 评论 / 举报 / 收藏（Comment / Report / Follow）

| 控制器 | 标准 CRUD 路径 | 特有接口 |
|---|---|---|
| CommentController | `api/v1/comment`（save/remove/update/list/getInfo/page） | DELETE `/comment` `deleteByUserId/{userId}`：根据用户 id 删除用户评论（注：注解参数名为 userId，方法形参为 id） |
| ReportController | `api/v1/report`（save/remove/update/list/getInfo/page） | — |
| FollowController | `api/v1/follow`（save/remove/update/list/getInfo/page） | — |

## 4. ml-sale 营销服务（45 个接口）

接口前缀：`api/v1`，在线文档：`http://localhost:24104/doc.html`

### 4.1 优惠券（CouponsController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/coupons` `save` | 保存优惠券 | Body：CouponsInsertDTO | boolean |
| DELETE | `/coupons` `remove/{id}` | 删除优惠券 | Path：id | boolean |
| PUT | `/coupons` `update` | 更新优惠券 | Body：Coupons | boolean |
| GET | `/coupons` `list` | 查询所有优惠券 | — | List\<Coupons\> |
| GET | `/coupons` `getInfo/{id}` | 根据主键获取优惠券 | Path：id | Coupons |
| GET | `/coupons` `page` | 分页查询优惠券 | Body：CouponsPageDTO | PageVO\<Coupons\> |
| GET | `/coupons` `getByCode/{code}` | 根据口令获取优惠券（兑换） | Path：code | Coupons |
| GET | `/coupons` `simpleList` | 查询可用的优惠券列表 | — | List\<Coupons\> |
| POST | `/coupons` `receive/{userId}/{couponId}` | 领取优惠券 | Path：userId + couponId | boolean |
| GET | `/coupons` `mine/{userId}` | 获取用户优惠券列表 | Path：userId | List\<Coupons\> |

### 4.2 秒杀（SeckillController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/seckill` `save` | 保存秒杀活动 | Body：SeckillInsertDTO | boolean |
| DELETE | `/seckill` `remove/{id}` | 删除秒杀活动 | Path：id | boolean |
| PUT | `/seckill` `update` | 更新秒杀活动 | Body：Seckill | boolean |
| GET | `/seckill` `list` | 查询所有秒杀活动 | — | List\<Seckill\> |
| GET | `/seckill` `getInfo/{id}` | 根据主键获取秒杀活动 | Path：id | Seckill |
| GET | `/seckill` `page` | 分页查询秒杀活动 | Body：SeckillPageDTO | PageVO\<Seckill\> |
| GET | `/seckill` `today` | 查询今日秒杀活动 | — | List\<Seckill\> |
| POST | `/seckill` `kill` | 秒杀 | Body：KillDTO | boolean |

**kill 请求体（KillDTO）**

| 参数 | 类型 | 校验 | 说明 |
|---|---|---|---|
| fkSeckillId | Long | 必填 | 秒杀活动 ID |
| fkUserId | Long | 必填 | 用户 ID |
| fkCourseId | Long | 必填 | 课程 ID |
| price | Double | 必填，>=0 | 课程原价 |
| skPrice | Double | 必填，>=0 | 课程秒杀价 |

**kill 业务校验**：活动不存在(5014) → 未开始(7000) → 已结束(7001) → 重复抢购(4010) → 库存不足(2000"库存不足，秒杀失败")。

### 4.3 秒杀明细（SeckillDetailController）

标准 CRUD：`api/v1/seckillDetail`（save/remove/update/list/getInfo/page），请求为 `SeckillDetailInsertDTO`/`SeckillDetailPageDTO`。

> 秒杀明细保存时会初始化 Redis 预扣库存（`seckill:course_count:{fkCourseId}` = sk_count）。

### 4.4 通知 / 横幅 / 新闻

| 控制器 | 标准 CRUD 路径 | 特有接口 |
|---|---|---|
| NoticeController | `api/v1/notice` | GET `/notice` `top/{n}`：查询置顶的 N 条通知，Path：n → List\<Notice\> |
| BannerController | `api/v1/banner` | GET `/banner` `top/{n}`：获取置顶的 n 条横幅 → List\<Banner\>；POST `/banner` `uploadImage`：上传横幅图片（MultipartFile：bannerFile）→ Result |
| ArticleController | `api/v1/article` | —（注：save/update 直接接收 Article 实体） |

## 5. ml-order 订单服务（22 个接口）

接口前缀：`api/v1`，在线文档：`http://localhost:24105/doc.html`

### 5.1 购物车（CartController）

标准 CRUD：`api/v1/cart`（save/remove/update/list/getInfo/page），请求为 `CartInsertDTO`/`CartPageDTO`。

> `cart/save` 重复加入同一课程会触发 `4008 购物车记录重复`。

### 5.2 订单（OrderController）

| 方法 | 路径 | 说明 | 参数 | 返回 |
|---|---|---|---|---|
| POST | `/order` `save` | 保存订单 | Body：OrderInsertDTO | boolean |
| DELETE | `/order` `remove/{id}` | 删除订单 | Path：id | boolean |
| PUT | `/order` `update` | 更新订单 | Body：Order | boolean |
| GET | `/order` `list` | 查询所有订单 | — | List\<Order\> |
| GET | `/order` `getInfo/{id}` | 根据主键获取订单 | Path：id | Order |
| GET | `/order` `page` | 分页查询订单 | Body：OrderPageDTO | PageVO\<Order\> |
| POST | `/order` `prePay` | 用户下单（预支付） | Body：PrePayDTO | Object（订单 ID） |
| POST | `/order` `getQrCode` | 获取支付二维码 | Body：QrCodeDTO | void（图片流输出到响应） |
| POST | `/order` `prePayNotify` | 支付回调 | Body：HttpServletRequest | boolean |
| POST | `/order` `cancel/{id}` | 取消订单 | Path：id | boolean |

**prePay 请求体（PrePayDTO）**

| 参数 | 类型 | 校验 | 说明 |
|---|---|---|---|
| fkUserId | Long | 必填 | 购买人用户 ID |
| courseIds | List\<Long\> | 必填，至少 1 个 | 课程主键列表 |
| totalAmount | Double | 必填，>=0 | 订单总金额 |
| fkCouponsId | Long | 选填 | 使用的优惠券 ID |

**prePay 业务逻辑**：校验是否已购买过（重复则 4000）→ 生成订单（sn=19 位随机数、payType=0、status=0）→ Feign 查用户/课程 → 保存订单明细 → 删除购物车对应记录 → 返回订单 ID。

**getQrCode 请求体（QrCodeDTO）**

| 参数 | 类型 | 校验 | 说明 |
|---|---|---|---|
| sn | String | 必填 | 订单编号 |
| payAmount | Double | 必填 | 实际支付金额 |

### 5.3 订单明细（OrderDetailController）

标准 CRUD：`api/v1/orderDetail`（save/remove/update/list/getInfo/page），请求为 `OrderDetailInsertDTO`/`OrderDetailPageDTO`。

## 6. 特殊接口说明

| 接口 | 说明 |
|---|---|
| `POST /user/loginByAccount` | 返回 LoginVO（用户+token+角色+菜单）；密码 BCrypt 校验；token 写 Redis TTL 30 分钟 |
| `GET /user/getPhoneCode?phone=` | 生成 6 位数字验证码，Key=`login_vcode:{phone}`；绑定/解绑场景另有 `bound_vcode:` / `unbound_vcode:` 前缀 |
| `POST /seckill/kill` | 分布式锁 + Redis 预扣库存 + RocketMQ 异步下单（详见总览文档 6.2） |
| `POST /order/prePayNotify` | 支付回调入口，接收 HttpServletRequest 原始请求体（支付宝回调），校验后更新订单状态 |
| `POST /order/getQrCode` | 根据订单编号与金额生成支付二维码，直接以图片流写入响应 |
| 上传类接口 | 统一 MultipartFile 接收，上传至 MinIO 桶 `mylesson`，返回文件名（存库时需拼接访问地址） |

## 7. 参数校验规则（ML.Regex）

| 规则常量 | 正则 | 适用 |
|---|---|---|
| USERNAME_RE | `^[a-zA-Z0-9]{4,20}$` | 账号 |
| PASSWORD_RE | `^[a-zA-Z0-9]{4,20}$` | 密码 |
| REALNAME_RE | `^[\u4e00-\u9fa5]{2,6}$` | 真实姓名 |
| NICKNAME_RE | `^[a-zA-Z0-9\u4e00-\u9fa5]{2,10}$` | 昵称 |
| ID_CARD_RE | `^[1-9]\d{5}(19|20)\d{2}((0[1-9])|(1[0-2]))(([0-2][1-9])|10|20|30|31)\d{3}[0-9Xx]$` | 身份证号 |
| PHONE_RE | `^1(3[0-9]|4[01456879]|5[0-35-9]|6[2567]|7[0-8]|8[0-9]|9[0-35-9])\d{8}$` | 手机号 |
| EMAIL_RE | `^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$` | 邮箱 |
| VCODE_RE | `^[0-9]{6}$` | 验证码 |
| TITLE_RE / AUTHOR_RE | `^.{1,42}$` | 标题/作者 |
| INFO_RE / CONTENT_RE | `^.{1,170}$` | 描述/内容 |
| CODE_RE / SN_RE | `^.{1,42}$` | 兑换口令/订单编号 |
| MENU_URL_RE | `^/[a-zA-Z]{0,256}$` | 菜单跳转地址 |
| MENU_ICON_RE | `^[a-zA-Z]{1,256}$` | 菜单图标 |
| PROVINCE_RE | `^[\u4e00-\u9fa5]{2,20}$` | 省份 |

校验失败统一返回 `code=3000`（参数校验异常），`coderMessage` 为第一条校验错误详情。

## 8. 已知问题

1. **前端调用了不存在的接口**：`ml-web` 的 `cms.ts` 中 `searchCourses` 调用 `GET /course-server/api/v1/course/search`，后端无此接口（课程搜索能力建议基于 `/course/page` 扩展）。
2. **网关路由配置在 Nacos**：仓库内无网关 routes 配置，本文档前缀映射以 `ml-web` 实际调用为准；若网关未配置对应前缀，请先在 Nacos `ml-gateway.yaml` 中补充。
3. **部分接口未加 `@Validated`**：仅登录、改密、改资料、验证码、下单、秒杀等接口启用了参数校验注解，标准 CRUD 的 save 接口（如 Banner/Article）依赖业务层校验。
