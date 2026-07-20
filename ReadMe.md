技术栈:SpringBoot Minio Redis Chroma
要做的事(todo):
1.针对List<Content> 进行pipline改造,确保流程可审查,易更新
2.支持会话节点拆分,即保留当前上下文的同时新增一个对话
3.用户退出会话后,将redis上的记忆进行卸载,存入数据库

已有功能:
多模态会话,
定制记忆 替换ImageContent为"[名字:描述]"文本,避免浪费过多token,后期考虑智能替换回url,
rag召回重排序,
文本知识库,支持大型文本文件进行上传,chunk+overlap+元数据 存入向量数据库
会话摘要,