# 每日 Token 配额练习指南（Redis 预扣 + 结算 + 落库）

> 场景三。前两份指南：`doc/并发限制练习指南.md`（ZSet 限并发）、`doc/会话互斥练习指南.md`（RLock + 看门狗）。
> 本指南的代码**需要你自己写**，骨架和提示已经在代码里留好了。

---

## 0. 场景背景

前两个场景限制的是「同时跑几个」和「同一个会话能不能并行」。这个场景限制的是**总量**：一个用户一天最多消耗多少 token。

业务上的差别很关键：

| | 场景一 ZSet 限并发 | 场景三 Token 配额 |
|---|---|---|
| 限的是什么 | 同时在跑的请求数 | 一天累计消耗的 token |
| 什么时候知道够不够 | 请求进入时就知道（数一下有几个） | 请求进入时**不知道**（还没调模型，不知道会吐出多少） |
| 怎么解决 | 直接数 | **先按估算值预扣，跑完按真实用量多退少补** |
| 超了返回什么 | 429 | **402**（额度用完了，和「并发过多」区分开） |

「预扣 + 结算」是这个场景的全部核心。

---

## 1. 三阶段闭环

```
请求进入
   │
   ├─ 1. 并发额度 tryAcquire            （场景一，已有）
   ├─ 2. 会话锁 tryLockSession          （场景二，已有）
   ├─ 3. 预扣 preDeduct                 ← 本场景：按 estimate-tokens=2000 占用
   │       └─ 不够 → 抛 402，不消耗算力
   ├─ 4. 跑 pipeline（ChatAgentStage 逐轮累加真实用量 → 写进 ctx）
   │
   └─ finally
        ├─ 5. 结算 settle   ← 本场景：diff = 真实 - 估算，多退少补 + 分模型累计 + 落库
        ├─ 6. 释放会话锁
        └─ 7. 释放并发额度
```

热路径里**只有两次 Redis 往返**（读配额缓存 + 预扣 Lua），MySQL 不在请求主链路上被高频读写。

---

## 2. Redis 键设计

| 键 | 类型 | 用途 | 谁写 |
|---|---|---|---|
| `ai:quota:{userId}:{yyyyMMdd}` | String | 当日已用（含预扣）token，限额判断就看它 | 预扣 / 结算 / 退还 |
| `ai:usage:{userId}:{yyyyMMdd}` | Hash | field = 模型名，value = 该模型当日累计 token | 结算 |
| `ai:quota:limit:{userId}` | String | 日额度缓存（`"100000"` 或 `"UNLIMITED"`） | `QuotaLimitResolver` |

MySQL 侧：

| 表 | 用途 |
|---|---|
| `ai_user_token_quota` | 日额度的**来源**（Redis 只是它的缓存） |
| `ai_user_daily_token_usage` | 用量的**最终落点**，唯一键 `(user_id, stat_date, model_name)` |

### 三个必须理解的设计点

**（1）为什么预扣要返回一个「凭证」而不是直接返回 `Long`？**

`preDeduct` 在 23:59:59 被调用、`settle` 在 00:00:01 被调用，如果结算时**重新算一次日期**，差额就会退到第二天的计数器上。所以`preDeduct`里只取一次日期，把 `quotaKey` + `statDate` 装进 `QuotaReservation` 带出去，`settle` **只用凭证里的键**。

**（2）为什么 member 是 requestId 而不是 sessionId？**（场景一的经验）
那是 ZSet 的坑。这里对应的坑是：**`settle` 里绝对不能重新 `LocalDate.now()`**。

**（3）为什么差额回补必须是负数？**

```java
long diff = actual - reservation.estimateTokens();   // 实际 180 - 估算 2000 = -1820
```
`INCRBY -1820` 就是「退 1820」。**很多人第一反应是 `SET actual`** —— 那会把并发请求的额度一起抹掉。

---

## 3. 现状盘点

### 已经写好的（不用你动）

| 位置 | 内容 |
|---|---|
| `module/chat/config/TokenQuotaProperties.java` | `ai.quota` 配置类：`estimateTokens=2000`、`defaultDailyLimit=10000`、`cacheTtlSeconds=600`、`counterTtlSeconds=172800` |
| `application-dev.yml` | 顶层 `ai.quota` 段 |
| `QuotaLimitResolver` | 默认额度与缓存 TTL 已改为读配置，不再硬编码 |
| `ChatPipelineContext` | 新增 `TOKEN_USAGE = "tokenUsage"` 常量 |
| `ChatAgentStage` | 用量累加与写回上下文 —— **你已完成**（`[步骤2-1]` `[步骤2-2]`） |
| `TokenQuotaService.PRE_DEDUCT_LUA` | 预扣脚本（一次原子完成「判限 + 累加 + 设过期」） |
| `TokenQuotaService.preDeduct` | 预扣并返回凭证 —— **你已完成**（`[步骤3-1]`） |
| `TokenQuotaService.SETTLE_LUA` | 结算脚本 —— **你已完成**（`[步骤4-1]`），但有一处 `tonumber` 要修，见排错速查表 |
| `TokenQuotaService.refund(...)` | 全额退还预扣额度（被拒绝的请求不该扣费） |
| `ChatPiplineController` | 注入、`resolveUserId`、`resolveModelName`、`preDeductOrRelease`、6 处 TODO |
| `TokenQuotaService.CODE_TOKEN_QUOTA_EXCEEDED` | 业务码 402 常量 |

### 你还要写的 5 个 TODO

| # | 位置 | 标记 | 写什么 |
|---|---|---|---|
| 1 | `TokenQuotaService` | `TODO [步骤4-2]` | `settle`：算真实用量 → 执行脚本 → 调落库 |
| 2 | `TokenQuotaService` | `TODO [步骤4-3]` | `persistDailyUsage`：组装实体 + 调 Mapper |
| 3 | `AiUserDailyTokenUsageMapper` | `TODO [步骤5-1]` | 累加式 upsert 的 `@Insert` SQL |
| 4 | `ChatPiplineController` `/fack` | `TODO [步骤6-1]`~`[步骤6-3]` | 预扣 / 取用量 / 结算 |
| 5 | `ChatPiplineController` `/stream` | `TODO [步骤6-4]`~`[步骤6-6]` | 同上，但预扣必须在 `return sink.asFlux()` 之前 |

**建议顺序**：先做 1、2（结算与落库）→ 3（Mapper）→ 4、5（接线）。
理由：结算可以用 `redis-cli` 单独验证，不用等模型真的跑起来。

---

## 步骤一：起依赖与环境准备

```powershell
Set-Location 'd:\code\project\backend\vllmpt'
docker compose -f doc/docker/docker-compose-env.yml up -d redis mysql rabbitmq chroma
```

确认三张表和测试数据都在（`doc/sql/init_user_quota.sql` 应该已经执行过）：

```powershell
docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev -e 'SELECT id,username FROM sys_user; SELECT user_id,daily_limit,quota_enabled FROM ai_user_token_quota; SELECT COUNT(*) FROM ai_user_daily_token_usage;'"
```

预期：`sys_user` 有 `1 test_user`，`ai_user_token_quota` 有 `1 100000 1`。

顺手准备一个观察窗口（另开一个终端）：

```powershell
# 每天把下面的 20261008 换成当天日期
docker exec -it vllmpt-redis redis-cli -a redis123 --no-auth-warning
# 进去之后：
#   GET ai:quota:1:20261008
#   HGETALL ai:usage:1:20261008
#   TTL ai:quota:1:20261008
#   GET ai:quota:limit:1
```

---

> **✅ 步骤二、步骤三已完成。**
> `ChatAgentStage` 的用量累加与 `TokenQuotaService.preDeduct` 就是你写的，这两步的讲解与参考实现已从本文档删除。
> 编号刻意保留不重排，以便与代码里的 `TODO [步骤2-x]` / `[步骤3-x]` 标记对应。
> 唯一遗留提醒：流式响应拿不到用量是**常态**（LangChain4j 不请求 `stream_options`），
> `settle` 里那条「取不到用量就按估算值计费」的兜底分支不是可选项。

---

## 步骤四：原子结算与落库

> **✅ [步骤4-1] 结算脚本已完成**（`TokenQuotaService.SETTLE_LUA`），讲解与参考实现已删除。
> 记得回去把 `local diff = ARGV[1]` 改成 `tonumber`，原因见排错速查表。

### TODO [步骤4-2]：settle

**整体必须包在 try/catch 里，catch 只打日志、绝不向外抛。**

原因：它运行在接入层的 `finally` 里。`finally` 中一条语句抛异常，**后面的语句就不会执行** —— 而后面那条正是「释放并发额度」。结算失败可以忍受，额度泄漏不能。

流程：

```java
if (reservation == null) { return; }
try {
    long actual = (usage != null && usage.totalTokenCount() != null)
            ? usage.totalTokenCount()
            : reservation.estimateTokens();      // 取不到就按估算值计费，差额 0
    // 走兜底分支时打 warn
    ...
    // 执行 SETTLE_LUA
    ...
    persistDailyUsage(reservation, usage, modelName, actual);
} catch (Exception e) {
    log.error(...);
}
```

> ⚠️ 差额回补与「是否限制额度」**无关**。不限制配额时，预扣脚本里照样执行了 `INCRBY estimate`，所以两种情况都要回补，否则那个计数器会越来越虚高。

### TODO [步骤4-3]：persistDailyUsage

按注释组装 `AiUserDailyTokenUsage` 并调用 `aiUserDailyTokenUsageMapper.upsertDailyUsage(row)`。要点：

- `usage == null` 直接 return（**没拿到用量就不落库**，否则会写一行不准的数据）；
- `reservation.userId()` 是 `String`，表里 `user_id` 是 `BIGINT`，需要 `Long.parseLong(...)`；
  解析失败（`anonymous`、`sessionId` 这类非数字标识）就 return 并打 debug —— **这不是错误**，只是这些标识本来就没有对应用户；
- `statDate` 是 `"yyyyMMdd"`，用 `LocalDate.parse(reservation.statDate(), STAT_DATE_FORMAT)` 转；
- `usage.inputTokenCount()` / `outputTokenCount()` 返回的是 **`Integer`，可能为 null**，需要转 `long` 并兜底 0；
- `estimated_cost` 这一列**不要带**（见步骤五）。

---

## 步骤五：累加式 upsert

### TODO [步骤5-1]

给 `AiUserDailyTokenUsageMapper.upsertDailyUsage` 补 `@Insert` 注解与 SQL。

**这张表唯一的坑：`ON DUPLICATE KEY UPDATE` 里必须写成「累加」。**

```sql
-- ✅ 正确：累加
total_tokens = total_tokens + #{totalTokens}

-- ❌ 错误：覆盖。第二次请求会把你第一次的累计值直接盖掉
total_tokens = #{totalTokens}
```

这个错误特别隐蔽：第一次请求一切正常，第二次开始数据就「不增反减」，而且不报错。

其他细节：

- 插入时 `request_count` 固定写 `1`，更新时固定 `+ 1`；
- 唯一键是 `(user_id, stat_date, model_name)`，SQL 里这三列都要出现在 `VALUES` 中；
- 不要带 `estimated_cost`：目前没有模型单价，传 `null` 会让 `estimated_cost = estimated_cost + NULL` 变成 `NULL`，把累计值抹成空。以后接入计价时再加，且要传 `BigDecimal.ZERO`。

> 在实际写 SQL 之前调用这个方法会抛 `BindingException: Invalid bound statement (not found)` —— 没有任何 SQL 注解、也没有对应 XML 的接口方法，MyBatis 不会为它注册语句。

---

## 步骤六：接入两个接口

顺序永远是：**并发额度 → 预扣 → 会话锁 → pipeline → (finally) 解锁 → 结算 → 释放并发额度**。

### /fack（同步）

- `TODO [步骤6-1]` 预扣 —— **必须写在 `try` 内部**。
  为什么：额度不足会抛 402，如果写在 `try` 外面（`tryAcquire` 旁边），`finally` 完全不执行，上面刚拿到的**并发额度就泄漏了**。
- `TODO [步骤6-2]` 取用量 —— `ctx.getAttribute(ChatPipelineContext.TOKEN_USAGE)`，**记得强转**（`ChatPipelineContext` 在业务代码里是裸类型，返回值是 `Object`）。
- `TODO [步骤6-3]` 结算 —— 按注释里的 `if (sessionLocked) { settle } else { refund }` 写。

### /stream（流式）

- `TODO [步骤6-4]` 预扣 —— 把 `QuotaReservation reservation = null;` 换成
  `QuotaReservation reservation = preDeductOrRelease(userId, requestId);`
  两条硬性要求：
  1. **必须发生在 `return sink.asFlux()` 之前**。一旦开始返回 `text/event-stream`（HTTP 200），就再也改不成 402 的 JSON 响应了 —— 和 429 完全同一个道理；
  2. 必须走 `preDeductOrRelease` 而不是直接调 `preDeduct`。它内部会在失败时**先退还并发额度再抛出**，否则预扣失败会让并发额度只增不减。
- `TODO [步骤6-5]` / `[步骤6-6]` 取用量与结算 —— 和 `/fack` 一致。

### 为什么是 `if (sessionLocked) settle else refund`？

会话锁没拿到 → 流水线根本没跑 → 模型一次都没调用 → **这次请求不该扣费**。

如果这里直接调 `settle`：`usage` 为 null 会被当成「拿不到用量」，于是按估算值计费 —— 一个被拒绝的请求白扣了 2000 token。这个 bug 在并发双流测试里会立刻暴露（第二个被拒的请求也会让 `GET ai:quota:1:{date}` 涨 2000）。

### 为什么结算必须放在 `release` 之前，又必须是「吞异常」的？

`finally` 的执行顺序是自上而下的，一条语句抛异常，后面的都不执行。所以：

```
unlock  →  settle/refund（自己吞异常）  →  release（最不能失败，放最后）
```

`settle` 和 `refund` 内部都已经做好了异常兜底，所以这条链不会断。

---

## 步骤七：验证

### 1) 编译

```powershell
Set-Location 'd:\code\project\backend\vllmpt'
.\mvnw.cmd -o -q compile -DskipTests
```

### 2) 正常路径（非流式，能拿到真实用量）

```powershell
$date = Get-Date -Format 'yyyyMMdd'
curl.exe -s -X POST http://localhost:8080/api/pipline/fack `
  -H "Content-Type: application/json" `
  -d "{\"userId\":\"1\",\"sessionId\":\"s1\",\"text\":\"用一句话解释什么是Redis\",\"modelName\":\"Qwen/Qwen3.6-35B-A3B\"}"
```

观察四个点：

```powershell
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning GET ai:quota:1:20261008
#   预期：一个远小于 2000 的数字（= 真实用量）。说明「预扣 2000 → 结算退回差额」生效
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning HGETALL ai:usage:1:20261008
#   预期：field 是模型名，value 是本次真实 token
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning TTL ai:quota:1:20261008
#   预期：接近 172800（2 天）
docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev -e 'SELECT user_id,stat_date,model_name,prompt_tokens,completion_tokens,total_tokens,request_count FROM ai_user_daily_token_usage WHERE user_id=1;'"
#   预期：新增一行，request_count=1
```

**关键验证：再发一次同样的请求**，然后

```powershell
# request_count 应变成 2，total_tokens 应该是两次之和（而不是被覆盖）
docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev -e 'SELECT total_tokens,request_count FROM ai_user_daily_token_usage WHERE user_id=1;'"
```

如果 `request_count` 还是 1、`total_tokens` 只是最后一次的值 → **SQL 写成了覆盖**，回步骤五。

### 3) 402 路径（不用真烧 token）

把额度调到比估算值略大，然后手动把计数器填到快满：

```powershell
# 1. 把日额度改成 3000
docker exec vllmpt-mysql sh -c "mysql -uroot -proot123 vllmpt_dev -e 'UPDATE ai_user_token_quota SET daily_limit=3000 WHERE user_id=1;'"
# 2. 必须删掉额度缓存，否则改的是 MySQL、读的还是 Redis
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning DEL ai:quota:limit:1
# 3. 手动把当日计数器填到 2900（2900 + 2000 > 3000）
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning SET ai:quota:1:20261008 2900
```

再发一次请求，预期拿到：

```json
{"code":402,"message":"今日 Token 额度已用完，请明天再试","data":null}
```

> ⚠️ 注意确认 `code` 是 402，**HTTP 状态码仍然是 200** —— 项目里 `GlobalExceptionHandler` 返回的是 `Result` 对象，业务码在响应体里。429 / 409 也是这个行为，保持一致。
>
> 验证完记得把额度改回 100000 并 `DEL ai:quota:limit:1`。

### 4) 被拒绝的请求不该扣费

保持上面的状态（额度 3000、计数器 2900）：

1. 先把 `ai:concurrent.limit` 临时改成 `1`，重启应用；
2. 用 `/stream` 发第一个请求（流还开着，占住并发额度）；
3. 再用**同一个 sessionId** 发第二个请求 —— 它会先过并发额度（被拒，429），拿不到会话锁的那条路径需要另测：把并发额度调大、用 `sessionId` 相同但让前一个流保持开启；
4. 第二个请求收到 `[ERROR] 该会话正在处理中，请稍候再试` 后，查：

```powershell
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning GET ai:quota:1:20261008
#   预期：还是 2900（没有被多扣 2000），说明走的是 refund 而不是 settle
```

日志里应该能看到 `退还预扣额度: userId=1, statDate=..., 退还=2000`。

### 5) 流式的兜底路径

用 `/stream` 发一个正常请求，观察日志：

```
结算时取不到真实用量，按预扣估算值计费: userId=1, statDate=..., estimate=2000, usageNull=true
```

以及：

```powershell
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning GET ai:quota:1:20261008
#   预期：比原来涨了 2000（因为没拿到真实用量，只能按估算值计费）
docker exec vllmpt-redis redis-cli -a redis123 --no-auth-warning HGETALL ai:usage:1:20261008
#   预期：这次没有新增 field —— persistDailyUsage 里 usage == null 会跳过落库
```

**这就是「流式拿不到用量」的实际后果**，也是我在步骤二里强调兜底是常态的原因。想真正修掉它，要在流式请求里带 `stream_options: {"include_usage": true}`，而 LangChain4j 1.16.2 没有暴露这个选项 —— 属于「加餐」范围（见文末）。

---

## 排错速查表

| 现象 | 原因 | 解法 |
|---|---|---|
| 怎么发都不被限 | `getScript()` 没传 `StringCodec.INSTANCE` | 显式传，并且所有 ARGV 统一 `String.valueOf(...)` |
| 编译找不到 `ReturnType.INTEGER` | Redisson 4.4.0 没这个枚举 | 用 `ReturnType.LONG` |
| `GET ai:quota:1:{date}` 一直是 2000 不降 | `settle` 没执行，或 `usage` 永远为 null | 看日志有没有「结算完成」；`/stream` 走 null 是正常的 |
| 计数器越来越小 / 变负数 | 结算 `INCRBY diff` 回补后没夹回 0 | 脚本里补 `if after < 0 then SET 0` |
| 计数器变成 `-1820` | 计数器键已过期，负数从 0 开始累加 | 同上 |
| 凭空多出一个 `ai:quota:1:{date} = 0`、且 `TTL` 是 `-1`（永不过期） | `local diff = ARGV[1]` 没 `tonumber`。Lua 里 `"0" ~= 0` 恒为 `true`，所以 `diff = "0"` 时也进了 `if`；而 `INCRBY` 在键不存在时会**先把它创建成 0**，之后 `after` 是 0 不小于 0，所以也不走 `EXPIRE` | `local diff = tonumber(ARGV[1])`（顺带把没用的 `res1`/`res2` 删掉，`INCRBY` 的返回值就是新值，不用再 `GET` 一次） |
| 差一天的数据对不上 | `settle` 里重新算了日期 | 只用 `reservation.quotaKey()` / `reservation.statDate()` |
| 改了 `daily_limit` 不生效 | 额度缓存没删 | `DEL ai:quota:limit:1` |
| `Invalid bound statement (not found)` | Mapper 方法还没写 `@Insert` | 步骤五 |
| `request_count` 不累加 | SQL 写成了 `= #{...}` | 改成 `= 列名 + #{...}` |
| 落库里一行都没有 | `userId` 不是数字（传了 sessionId） | 请求里带上 `"userId":"1"` |
| 被拒绝的请求也扣了 2000 | `finally` 里没区分 `sessionLocked` | 用 `if (sessionLocked) settle else refund` |
| 并发额度只增不减（402 之后） | 预扣写在 `try` 外面 | 挪进 `try` 内部，或先 `release` 再抛 |
| `/stream` 返回 406 | `produces` 没放宽 | 已配置 `{text/event-stream, application/json}`，别删 |
| 结算异常导致并发额度泄漏 | `settle` 往外抛异常，阻断了 `release` | `catch (Exception e) { log.error(...) }`，绝不 rethrow |

---

## 附录：参考实现

> 建议先自己写完、把步骤七的 1)2)3) 跑通，再翻这里。

> A（`ChatAgentStage`）、B（`preDeduct`）、C（`SETTLE_LUA`）三份参考实现已删除 —— 你已经自己写完了。
> 剩下的 D~H 是还没写的部分。

### D. `TokenQuotaService.settle`

```java
public void settle(QuotaReservation reservation, TokenUsage usage, String modelName) {
    if (reservation == null) {
        return;
    }
    try {
        long actual;
        if (usage != null && usage.totalTokenCount() != null) {
            actual = usage.totalTokenCount();
        } else {
            actual = reservation.estimateTokens();
            log.warn("结算时取不到真实用量，按预扣估算值计费: userId={}, statDate={}, estimate={}, usageNull={}",
                    reservation.userId(), reservation.statDate(), reservation.estimateTokens(), usage == null);
        }

        long diff = actual - reservation.estimateTokens();
        String usageKey = RedisKey.usage(reservation.userId(), reservation.statDate());

        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                SETTLE_LUA,
                RScript.ReturnType.LONG,
                List.of(reservation.quotaKey(), usageKey),
                String.valueOf(diff),
                modelName,
                String.valueOf(actual),
                String.valueOf(tokenQuotaProperties.getCounterTtlSeconds()));

        log.debug("结算完成: userId={}, statDate={}, estimate={}, actual={}, diff={}",
                reservation.userId(), reservation.statDate(), reservation.estimateTokens(), actual, diff);

        persistDailyUsage(reservation, usage, modelName, actual);
    } catch (Exception e) {
        log.error("结算失败（不影响主流程与资源释放）: userId={}, statDate={}, modelName={}",
                reservation.userId(), reservation.statDate(), modelName, e);
    }
}
```

### E. `persistDailyUsage`

```java
private void persistDailyUsage(QuotaReservation reservation, TokenUsage usage,
                               String modelName, long actual) {
    if (usage == null) {
        return;
    }
    Long dbUserId;
    try {
        dbUserId = Long.parseLong(reservation.userId());
    } catch (NumberFormatException e) {
        log.debug("userId 不是数字，跳过用量落库: userId={}", reservation.userId());
        return;
    }

    AiUserDailyTokenUsage row = new AiUserDailyTokenUsage();
    row.setUserId(dbUserId);
    row.setStatDate(LocalDate.parse(reservation.statDate(), STAT_DATE_FORMAT));
    row.setModelName(modelName);
    row.setPromptTokens(toLong(usage.inputTokenCount()));
    row.setCompletionTokens(toLong(usage.outputTokenCount()));
    row.setTotalTokens(actual);
    row.setRequestCount(1);

    int affected = aiUserDailyTokenUsageMapper.upsertDailyUsage(row);
    log.debug("用量落库: userId={}, statDate={}, modelName={}, total={}, affected={}",
            dbUserId, row.getStatDate(), modelName, actual, affected);
}

private static long toLong(Integer value) {
    return value == null ? 0L : value.longValue();
}
```

### F. `AiUserDailyTokenUsageMapper`

```java
@Insert("""
    INSERT INTO ai_user_daily_token_usage
        (user_id, stat_date, model_name, prompt_tokens, completion_tokens,
         total_tokens, request_count)
    VALUES (#{userId}, #{statDate}, #{modelName}, #{promptTokens}, #{completionTokens},
            #{totalTokens}, #{requestCount})
    ON DUPLICATE KEY UPDATE
        prompt_tokens     = prompt_tokens     + #{promptTokens},
        completion_tokens = completion_tokens + #{completionTokens},
        total_tokens      = total_tokens      + #{totalTokens},
        request_count     = request_count     + 1
    """)
int upsertDailyUsage(AiUserDailyTokenUsage usage);
```

> 用 `#{promptTokens}` 而不是 MySQL 的 `VALUES(prompt_tokens)`：`VALUES()` 在 8.0.20+ 已被标记为过时，直接写参数值语义更清楚，也不依赖 MySQL 版本。
> `deleted` 列不用带，建表默认值是 0。

### G. `/fack` 的 finally

```java
} finally {
    if (sessionLocked) { sessionLockService.unlockSession(sessionId); }

    if (sessionLocked) {
        tokenQuotaService.settle(reservation, usage, resolveModelName(ctx));
    } else {
        tokenQuotaService.refund(reservation);
    }

    concurrentLimitService.release(userId, requestId);
}
```

### H. `/stream` 的预扣

```java
// return sink.asFlux() 之前
QuotaReservation reservation = preDeductOrRelease(userId, requestId);
```

lambda 的 `finally` 与 `/fack` 完全一致。

---

## 加餐：可以继续练的方向

1. **让流式也能拿到真实用量**
   LangChain4j 1.16.2 的 `OpenAiStreamingChatModel` 不暴露 `stream_options`。要拿到流式 usage，得自己包一层 HTTP 调用（或换用支持该选项的模型实现），拿到 `include_usage` 的最后一帧。这是当前方案最大的精度缺口。

2. **月度配额**
   把 `AIUserTokenQuota.monthlyLimit` 用起来，键改成 `ai:quota:{userId}:{yyyyMM}`。注意跨月结算和日额度是两套计数器，预扣要同时判两层。

3. **配额变更时主动失效缓存**
   现在改 `ai_user_token_quota` 必须手动 `DEL ai:quota:limit:{userId}`。可以在这个表的写入路径（或一个管理接口）里统一删缓存。

4. **`estimated_cost` 与模型单价**
   加一张模型单价配置，结算时算 `单价 × total_tokens` 累加进 `estimated_cost`。**记得传 `BigDecimal.ZERO` 而不是 `null`**，否则 `estimated_cost + NULL = NULL`。

5. **把对账搬到独立任务**
   目前落库在请求线程里同步做。量大了要换成定时任务批量对账（补偿 Redis 与 MySQL 的差异），但那时必须保证「重复对账不重复计数」—— 靠的就是步骤五那个累加式 upsert + 唯一键。

6. **加指标**
   预扣失败率、结算差额分布（`estimate - actual` 的直方图）能直接告诉你 `estimate-tokens` 该调成多少。现在 2000 是拍的，理想值应该接近 P90 的真实用量。
