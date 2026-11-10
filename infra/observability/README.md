# 账号注销执行告警规则

本目录只放账号注销执行任务（SPEC-PLAT-004，L1-8）的 Prometheus 告警规则与说明，不铺 Grafana 面板、不配 Alertmanager 路由、不写 recording rules。指标口径与业务语义见 `docs/specs/platform.md` 的 AC-25/AC-26/AC-27，本地启动与开关见 [local-dev-guide.md](../../docs/local-dev-guide.md#48-账号注销执行本地)。

## 1. 文件

| 文件 | 用途 |
|---|---|
| `prometheus-rules.yml` | 两条告警：执行持续失败、执行持续跳过 |

抓取配置（`prometheus.yml`）与监控栈编排属于 M0-40、M0-41，尚未落地；本目录不提供，部署侧按服务 `/actuator/prometheus` 自行接入。

## 2. 指标契约

Micrometer 计数器经 `/actuator/prometheus` 暴露时统一带 `_total` 后缀，标签名保持驼峰：

| 指标 | 类型 | 标签 | 代码位置 | 语义 |
|---|---|---|---|---|
| `lp_auth_account_cancellation_executed_total` | counter | 无 | `AccountCancellationExecutionJob.METRIC_EXECUTED` | 每成功执行一条到期申请 +1 |
| `lp_auth_account_cancellation_execute_failed_total` | counter | `userId` | `AccountCancellationExecutionJob.METRIC_FAILED` | 单条申请在扫描中抛错、整体回滚 +1；下一轮仍会被扫描 |
| `lp_auth_account_cancellation_execute_skipped_total` | counter | `userId` | `AccountCancellationExecutionJob.METRIC_SKIPPED` | 到期但未执行 +1，原因见 WARN 日志的 `reason`：`blocked` / `already-claimed` / `status-changed` |

使用注意：

- 计数器随进程重启归零，`increase()` 已处理重置；不要用裸值做阈值。
- 执行类计数器在**首次自增时才注册**：任务还没执行、失败或跳过任何一条申请时，`/actuator/prometheus` 里看不到对应序列（已确认 `applied_total` 立即出现、`executed` 等要等首次触发），这不是埋点缺失，规则在序列缺失时保持静默。
- `userId` 标签的基数上限是「单次扫描批大小 200 × 未完成申请的账号数」，不会无限增长；但按用户查询时不要把它做成常驻面板维度。
- 失败不等于丢单：执行失败会回滚申请状态，下一轮扫描重试，**没有重试上限**，所以持续性故障必须靠告警兜底。

## 3. 规则与阈值依据

| 告警 | 表达式摘要 | 持续 | 级别 | 阈值依据 |
|---|---|---|---|---|
| `LpAuthAccountCancellationExecuteFailed` | `sum by (userId)(increase(...execute_failed_total[15m])) >= 3` | 5 分钟 | critical | 扫描间隔默认 60 秒，同一账号 15 分钟内失败 3 次说明重试没有收敛（trade 不可达、匿名化写入被拒），删除永远不会完成 |
| `LpAuthAccountCancellationExecuteSkippedPersistent` | `sum by (userId)(increase(...execute_skipped_total[24h])) >= 48` | 10 分钟 | warning | 直接取 AC-12 的「同一用户 24 小时内跳过达到 48 次」；按 60 秒扫描换算约等于持续阻塞 48 分钟 |

跳过的三种原因共用一条计数。`already-claimed` 在多副本竞争时属正常现象，只有在 24 小时内达到 48 次才值得关注：要么副本在空转，要么另一副本把同一条申请长期占而不用。

## 4. 接入方式

Prometheus 加载本文件即可，路径按部署环境挂载：

```yaml
rule_files:
  - /etc/prometheus/rules/prometheus-rules.yml
```

Alertmanager 的接收人、静默与升级策略不在本仓库范围内；规则只负责触发，`severity` 与 `service`、`domain` 标签供路由使用。

## 5. 本地校验

```bash
# 有 promtool 时优先用它；本机未安装时用 YAML 解析兜底
promtool check rules infra/observability/prometheus-rules.yml

# 兜底：仅验证 YAML 语法
python3 -c "import yaml;yaml.safe_load(open('infra/observability/prometheus-rules.yml'));print('yaml ok')"

# 指标形状：打开执行任务后观察三类计数
curl -s http://127.0.0.1:8101/actuator/prometheus | grep lp_auth_account_cancellation
```

## 6. 边界

- 申请、撤销两个动作的计数器（`applied`、`withdrawn`）与缓存失效失败（`cache_invalidate.failed`，AC-33）、trade 侧降级（`order.account_guard.degraded`，AC-24）不在本片范围，后续按同样方式补规则。
- 规则不判断「多久没有执行过任何申请」。静默可能只是没有到期申请，纳入告警会产生大量误报；需要时用运营看板而不是告警表达。
