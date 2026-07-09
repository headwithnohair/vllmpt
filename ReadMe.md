技术栈:SpringBoot Minio Redis Chroma
要做的事(todo):
1.知识库 (向量化文本,图片)
2.支持会话节点拆分,即保留当前上下文的同时新增一个对话

已有功能:
多模态会话,
定制记忆 替换ImageContent为"[名字:描述]"文本,避免浪费过多token,后期考虑智能替换回url,
会话摘要,