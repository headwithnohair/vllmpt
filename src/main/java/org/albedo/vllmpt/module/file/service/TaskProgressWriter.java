package org.albedo.vllmpt.module.file.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.redis.RedisKey;
import org.albedo.vllmpt.module.file.config.FileTaskProperties;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务实时进度写入器（场景6）：封装 Redis Hash 的读写约定，消费者只管调用。
 * <p>
 * 约定见 {@link RedisKey#fileTaskProgress(String)}：key = {@code ai:file:task:progress:{taskId}}，
 * Hash 字段：{@code status / stage / percent / message / updatedAt}。
 * <p>
 * 两个实现要点（对照文档 3.3）：
 * <ul>
 *   <li><b>StringCodec</b>：Redisson 默认 codec 存的是二进制，redis-cli 里是乱码；
 *       这里显式用 {@link StringCodec#INSTANCE}，调试时 {@code HGETALL} 直接可读；</li>
 *   <li><b>TTL 续期</b>：每次写入都 expire 一次（{@code app.file-task.progress-ttl-seconds}），
 *       兜底清理异常中断后残留的 key，不会永久占内存。</li>
 * </ul>
 * 终态（SUCCESS/FAILED）的 Hash 不删除，由 TTL 自然过期——前端可能晚连 SSE，
 * 需要回放最后一帧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressWriter {

    private final RedissonClient redissonClient;
    private final FileTaskProperties props;

    /**
     * 写「进行中」进度（各阶段消费者在开始时 / 完成阶段时调用）。
     *
     * @param taskId  任务 ID
     * @param stage   阶段标识：PARSING / CHUNKING / EMBEDDING
     * @param percent 0-100（约定：解析完 30、分块完 60、向量化完 90）
     * @param message 展示文案
     */
    public void running(String taskId, String stage, int percent, String message) {
        Map<String, String> fields = new HashMap<>();
        fields.put("status", "RUNNING");
        fields.put("stage", stage);
        fields.put("percent", String.valueOf(percent));
        fields.put("message", message);
        fields.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        write(taskId, fields);
    }

    /** 写「成功」终态（全链路完成时调用）。 */
    public void success(String taskId) {
        Map<String, String> fields = new HashMap<>();
        fields.put("status", "SUCCESS");
        fields.put("stage", "DONE");
        fields.put("percent", "100");
        fields.put("message", "处理完成");
        fields.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        write(taskId, fields);
    }

    /**
     * 写「失败」终态（重试耗尽 / 超时判定时调用）。
     * 不覆盖 percent（保留失败时所在阶段的进度，便于排查）。
     */
    public void failed(String taskId, String message) {
        Map<String, String> fields = new HashMap<>();
        fields.put("status", "FAILED");
        fields.put("message", message);
        fields.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        write(taskId, fields);
    }

    /**
     * 读取进度快照（SSE 首帧 / 超时检查 / 排障用）。
     *
     * @return 字段 Map；key 不存在时返回空 Map
     */
    public Map<String, String> snapshot(String taskId) {
        return map(taskId).readAllMap();
    }

    private void write(String taskId, Map<String, String> fields) {
        RMap<String, String> map = map(taskId);
        map.putAll(fields);
        // 每写一次续一次期，防止长时间任务中途 key 过期
        map.expire(Duration.ofSeconds(props.getProgressTtlSeconds()));
    }

    private RMap<String, String> map(String taskId) {
        return redissonClient.getMap(RedisKey.fileTaskProgress(taskId), StringCodec.INSTANCE);
    }
}
