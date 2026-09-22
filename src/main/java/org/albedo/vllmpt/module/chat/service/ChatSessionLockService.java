package org.albedo.vllmpt.module.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 会话级互斥锁：同一个 sessionId 同一时刻只允许一个推理任务在执行。
 */
@Slf4j
@Service
public class ChatSessionLockService {

    private final RedissonClient redissonClient;

    public ChatSessionLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }


    /**
     * 尝试获取会话锁，拿到返回 true。
     * @return true 表示拿到锁（或无需加锁），可以执行推理；false 表示该会话正在处理中
     */


    public boolean tryLockSession(String sessionId) {

        if(sessionId==null || sessionId.trim().isEmpty())
        {
            return  true;
        }

        try {
            RLock  lock= redissonClient.getLock(RedisKey.sessionLock(sessionId));
            if (lock.tryLock(0,-1,TimeUnit.SECONDS)){//leaseTime 必须是 -1 才启用看门狗
                return true;
            }else{
                log.warn("会话处理中, sessionId:{}",sessionId);
                return false;
            }
        }catch (InterruptedException e){
            log.warn("获取会话锁时被中断, sessionId: {}", sessionId, e);
            Thread.currentThread().interrupt();
            return  false;
        }

    }

    /**
     * 释放会话锁。
     *本方法总是在 {@code finally} 中调用，抛出异常会覆盖掉真正的业务异常
     */
    public void unlockSession(String sessionId) {

        if (sessionId==null || sessionId.trim().isEmpty()){
            return;
        }

        try {
                RLock rLock= redissonClient.getLock(RedisKey.sessionLock(sessionId));


                if(rLock.isHeldByCurrentThread()){
                    rLock.unlock();
                }
            }catch (Exception e){

                log.error("释放会话锁异常, sessionId: {}", sessionId, e);
            }
    }

}
