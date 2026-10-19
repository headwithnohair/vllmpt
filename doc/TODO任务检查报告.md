# TODO 任务完成情况检查报告

> 检查日期：2026-10-10
> 检查范围：`.codebuddy/plans/` 下三个计划 + 对应代码实现

---

## 一、任务完成情况总览

### 计划一：数据库基础设施（`redis-daily-token-quota`）

| 任务 | 状态 |
|---|---|
| DDL 建表脚本（`doc/sql/init_user_quota.sql`） | ✅ 完成 |
| 实体类（SysUser / AiUserTokenQuota / AiUserDailyTokenUsage） | ✅ 完成 |
| Mapper 接口（三个） | ✅ 完成 |
| `@MapperScan` 扫描配置 | ✅ 完成 |
| 方案文档（`doc/每日Token配额方案.md`） | ✅ 完成 |
| 编译验证 | ✅ 完成 |

### 计划二：每日 Token 配额全链路（`redis-daily-token-quota-full`）

| 任务 | 状态 | 说明 |
|---|---|---|
| TokenQuotaProperties 配置类 | ✅ 完成 | 配置文件与解析器已接入 |
| ChatAgentStage 用量采集 | ✅ 完成 | 逐轮累加 + 写入上下文 `TOKEN_USAGE` |
| preDeduct 预扣返回凭证 | ✅ 完成 | 返回 `QuotaReservation`，超额抛 402 |
| SETTLE_LUA 结算脚本 | ✅ 完成 | 差额回补 + 分模型累计 + 过期设置 |
| settle 方法 | ✅ 完成 | 但缺少兜底分支 warn 日志（见 Bug 4） |
| persistDailyUsage 落库 | ✅ 完成 | 但存在 NPE 风险（见 Bug 2） |
| AiUserDailyTokenUsageMapper upsert | ✅ 完成 | 但存在参数名错误（见 Bug 1） |
| ChatPiplineController `/fack` 接入 | ✅ 完成 | 预扣在 try 内、finally 区分 settle/refund |
| ChatPiplineController `/stream` 接入 | ✅ 完成 | 预扣在 `return sink.asFlux()` 之前 |
| 练习指南文档（`doc/每日Token配额练习指南.md`） | ✅ 完成 | |

### 计划三：Redis Pub/Sub 任务取消（`redis-task-cancel-pubsub`）

| 任务 | 状态 |
|---|---|
| 基础样板（RedisKey / CancelProperties / TaskCancelCommand） | ❌ 未开始 |
| ActiveTaskRegistry 任务注册表 | ❌ 未开始 |
| ChatCancelService 取消服务 | ❌ 未开始 |
| ChatAgentStage 取消改造 | ❌ 未开始 |
| ChatPiplineController 取消接线 | ❌ 未开始 |
| 练习指南与文档 | ❌ 未开始 |
| 编译验证 | ❌ 未开始 |

> 计划文件名已标注「未完成」，7 个任务全部为 `pending` 状态，代码中也未发现相关实现。

---

## 二、已发现的关键 Bug（4 个）

### Bug 1：AiUserDailyTokenUsageMapper — `#{}` 参数名错误（严重）

**文件**：`src/main/java/org/albedo/vllmpt/module/quota/mapper/AiUserDailyTokenUsageMapper.java`

**问题**：当前 SQL 使用了**数据库列名**（下划线格式），但 MyBatis 的 `#{}` 应该使用 **Java 属性名**（驼峰格式）。

实体的 Java 属性名是 `userId`、`statDate`、`modelName`、`promptTokens` 等驼峰格式。写成 `#{user_id}` 会导致 MyBatis 调用 `getUser_id()` 方法，运行时直接报错。

**当前错误写法**：

```java
@Insert("INSERT INTO ai_user_daily_token_usage (user_id, stat_date, model_name, prompt_tokens, " +
        "completion_tokens, total_tokens, request_count) " +
        "values (#{user_id},#{stat_date}," +
        "#{model_name},#{prompt_tokens},#{completion_tokens},#{total_tokens},1) " +
        "ON DUPLICATE KEY UPDATE " +
        "request_count = request_count+1," +
        "prompt_tokens = prompt_tokens+#{prompt_tokens}," +
        "completion_tokens = completion_tokens + #{completion_tokens}," +
        "total_tokens = total_tokens + #{total_tokens}"
        )
int upsertDailyUsage(AiUserDailyTokenUsage usage);
```

**正确写法**（参数改为驼峰属性名）：

```java
@Insert("INSERT INTO ai_user_daily_token_usage (user_id, stat_date, model_name, prompt_tokens, " +
        "completion_tokens, total_tokens, request_count) " +
        "values (#{userId}, #{statDate}, " +
        "#{modelName}, #{promptTokens}, #{completionTokens}, #{totalTokens}, 1) " +
        "ON DUPLICATE KEY UPDATE " +
        "request_count = request_count+1," +
        "prompt_tokens = prompt_tokens+#{promptTokens}," +
        "completion_tokens = completion_tokens + #{completionTokens}," +
        "total_tokens = total_tokens + #{totalTokens}"
        )
int upsertDailyUsage(AiUserDailyTokenUsage usage);
```

---

### Bug 2：persistDailyUsage — inputTokenCount / outputTokenCount 的 NPE 风险

**文件**：`src/main/java/org/albedo/vllmpt/module/chat/service/TokenQuotaService.java`

**问题**：`usage.inputTokenCount()` 和 `usage.outputTokenCount()` 返回 `Integer`（可能为 null），直接用 `Long.valueOf()` 会抛 `NullPointerException`。练习指南明确要求转 `long` 并兜底 0。

**当前代码**：

```java
row.setPromptTokens(Long.valueOf(usage.inputTokenCount()));
row.setCompletionTokens(Long.valueOf(usage.outputTokenCount()));
```

**修复方式**（加一个安全转换方法）：

```java
row.setPromptTokens(toLong(usage.inputTokenCount()));
row.setCompletionTokens(toLong(usage.outputTokenCount()));
// ...
private static long toLong(Integer value) {
    return value == null ? 0L : value.longValue();
}
```

---

### Bug 3：SETTLE_LUA — `local diff = ARGV[1]` 缺少 `tonumber`

**文件**：`src/main/java/org/albedo/vllmpt/module/chat/service/TokenQuotaService.java`

**问题**：Lua 中 `"0" ~= 0` 恒为 true，diff 为 `"0"` 时也会进入 `if diff ~= 0` 分支；`INCRBY` 在键不存在时会先把它创建成 0，之后 `after` 是 0 不小于 0，于是不设置过期时间，导致产生一个**永不过期的** `ai:quota:1:{date} = 0` 键。

**当前错误写法**：

```lua
local diff =       ARGV[1]
```

**正确写法**：

```lua
local diff = tonumber(ARGV[1])
```

---

### Bug 4：settle 缺少兜底分支的 warn 日志

**文件**：`src/main/java/org/albedo/vllmpt/module/chat/service/TokenQuotaService.java`

**问题**：当 `usage` 为 null 或 `totalTokenCount` 为 null 时，按估算值计费但没打 warn 日志，排查问题时会很难定位（尤其是流式响应拿不到用量的常态化场景）。

**当前代码**：

```java
long actual = (usage != null && usage.totalTokenCount() != null)
        ? usage.totalTokenCount()
        : reservation.estimateTokens();
```

**修复方式**（补一条 warn 日志）：

```java
long actual;
if (usage != null && usage.totalTokenCount() != null) {
    actual = usage.totalTokenCount();
} else {
    actual = reservation.estimateTokens();
    log.warn("结算时取不到真实用量，按预扣估算值计费: userId={}, statDate={}, estimate={}, usageNull={}",
            reservation.userId(), reservation.statDate(), reservation.estimateTokens(), usage == null);
}
```

---

## 三、总结

| 计划 | 状态 |
|---|---|
| 数据库基础设施 | ✅ 全部完成 |
| 每日 Token 配额全链路 | ⚠️ 功能已实现，但有 4 个 bug 需修复 |
| Redis Pub/Sub 任务取消 | ❌ 完全未开始（计划文件名已标注「未完成」） |

**建议**：

1. 优先修复上述 4 个 bug（尤其是 Bug 1 Mapper 参数名和 Bug 3 的 `tonumber`，两者都会导致运行时异常或脏数据）；
2. 按练习指南「步骤七」的验证流程跑通功能（编译 → 正常路径 → 402 路径 → 被拒请求不扣费 → 流式兜底）；
3. 确认配额功能稳定后，再开始计划三（任务取消）的开发。
