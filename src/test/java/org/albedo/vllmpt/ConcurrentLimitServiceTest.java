package org.albedo.vllmpt;

import org.albedo.vllmpt.module.chat.config.ConcurrentLimitProperties;
import org.albedo.vllmpt.module.chat.service.ConcurrentLimitService;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentLimitServiceTest {

    private RedissonClient client() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setPassword("redis123")
                .setDatabase(0);
        return Redisson.create(config);
    }

    @Test
    void 超过limit应拒绝_释放后可再次获取() {
        RedissonClient redisson = client();

        ConcurrentLimitProperties props = new ConcurrentLimitProperties();
        props.setLimit(3);
        props.setWindowSeconds(300);
        props.setKeyTtlSeconds(600);

        ConcurrentLimitService service = new ConcurrentLimitService(redisson, props);

        String user = "test-" + System.currentTimeMillis();

        // 前 3 次应放行
        assertTrue(service.tryAcquire(user, "r1"));
        assertTrue(service.tryAcquire(user, "r2"));
        assertTrue(service.tryAcquire(user, "r3"));

        // 第 4 次应被拒绝
        assertFalse(service.tryAcquire(user, "r4"));

        // 计数应为 3
        assertEquals(3, service.currentCount(user));

        // 释放一个后应能再拿一个
        service.release(user, "r1");
        assertEquals(2, service.currentCount(user));
        assertTrue(service.tryAcquire(user, "r5"));

        // 清理现场
        redisson.getKeys().delete("ai:concurrent:" + user);
        redisson.shutdown();
    }
}
