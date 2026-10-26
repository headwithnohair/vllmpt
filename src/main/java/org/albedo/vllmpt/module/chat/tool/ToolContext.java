package org.albedo.vllmpt.module.chat.tool;

/**
 * 工具执行上下文。
 * <p>
 * 工具方法本身不接收 userId/sessionId/bizType，避免模型伪造这些安全相关参数；
 * 由 {@code ChatAgentStage} 在进入工具循环前写入，工具实现通过 {@link ToolContextHolder} 读取。
 *
 * @param userId   当前用户
 * @param sessionId 会话 ID
 * @param bizType  业务类型（审批场景为 LEAVE 等）
 * @param toolGroup 工具分组（approval / public）
 */
public record ToolContext(Long userId, String sessionId, String bizType, String toolGroup) {
}
