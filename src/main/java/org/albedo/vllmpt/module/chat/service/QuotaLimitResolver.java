package org.albedo.vllmpt.module.chat.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.albedo.vllmpt.module.chat.model.vo.QuotaLimit;
import org.albedo.vllmpt.module.quota.mapper.AiUserTokenQuotaMapper;
import org.albedo.vllmpt.module.quota.model.entity.AiUserTokenQuota;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;


@Service
public class QuotaLimitResolver {


    private final  AiUserTokenQuotaMapper aiUserTokenQuotaMapper;

    // ⚠️ 暂时还没用到：等加缓存层（读/回填 ai:quota:limit:{userId}）时才会用上
    private final  RedissonClient redissonClient;

    public QuotaLimitResolver(AiUserTokenQuotaMapper aiUserTokenQuotaMapper, RedissonClient redissonClient) {
        this.aiUserTokenQuotaMapper = aiUserTokenQuotaMapper;
        this.redissonClient = redissonClient;
    }


    // ✅ 签名正确：record 是"类型"、方法另写，这样分开才对
    //    userId 是入参，由调用方（TokenQuotaService.preDeduct）传进来
    public QuotaLimit resolve(String userId) {

        // ---------- 第一步：读缓存（还没写）----------
        // ❌ 缺：先 GET ai:quota:limit:{userId}
        //    命中就直接 return，不走下面的 SQL
        //    推荐的值编码：数字字符串 = 额度；"UNLIMITED" = 不限制

        // ---------- 第二步：miss 才回源 SQL ----------
        LambdaQueryWrapper<AiUserTokenQuota> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiUserTokenQuota::getUserId,userId);
        // ✅ selectOne 补了 false，多条时不会再抛 TooManyResultsException
        AiUserTokenQuota aiUserTokenQuota= aiUserTokenQuotaMapper.selectOne(queryWrapper,false);

        // ---------- 第三步：判定 ----------
        // ⚠️ 下面这个分支把两种情况合并了，但它们含义完全相反：
        //    ① aiUserTokenQuota == null  → 没有配额记录 → 用默认额度 → enabled = true（你这样是对的）
        //    ② getQuotaEnabled() != 1    → 有记录但显式关闭了配额 → 应该是 enabled = false（不限制）
        // ❌ 现在两种情况都返回 enabled = true，②会被当成"限额 10000"，语义反了
        // ❌ 而且 10000L 是硬编码，应该来自配置项 ai.quota.default-daily-limit
        if (aiUserTokenQuota==null ||
                aiUserTokenQuota.getQuotaEnabled() !=1 )
        {
            return  new QuotaLimit(10000L,true);
        }

        // ❌ 这里还缺两条时间判定，写法如下：
        //
        //    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        //
        //    boolean notYetEffective = aiUserTokenQuota.getEffectiveFrom() != null
        //            && aiUserTokenQuota.getEffectiveFrom().isAfter(now);
        //    boolean expired = aiUserTokenQuota.getEffectiveTo() != null
        //            && aiUserTokenQuota.getEffectiveTo().isBefore(now);
        //
        //    注意三点：
        //      1) 必须先判 null —— null 表示"不限"，effective_from 为 null 就是"立即生效"
        //      2) 用 isAfter / isBefore 直接比，别自己去拆年月日
        //      3) 必须显式传 Asia/Shanghai，否则 JVM 默认时区不同会算错
        //
        //    notYetEffective → enabled = false，并且缓存 TTL 要缩短到 effectiveFrom
        //    expired         → enabled = false
        //    都不是           → enabled = true, dailyLimit = aiUserTokenQuota.getDailyLimit()

        // ---------- 第四步：回填缓存 + 返回 ----------
        // ❌ 还没写：把结果写回 ai:quota:limit:{userId}（默认值也要写，防穿透），然后 return
        // ❌ 直接 return null 会让调用方 NPE

        return  null;
    }





}
