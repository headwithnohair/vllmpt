秘塔 智能审批助手     
技术栈:SpringBoot Minio Redis Chroma   
todo:  
1.针对List<Content> 进行pipline改造,确保流程可审查,易更新  
2.支持会话节点拆分,即保留当前上下文的同时新增一个对话  
3.用户退出会话后,将redis上的记忆进行卸载,存入数据库  
4.结合状态机 完成ReAct+Tools功能,  
5.接入飞书,钉钉审批api.  完成  对异常审批的提醒 

已有功能:  
从0到1实现AiSevice  
多模态会话,  
定制记忆 替换ImageContent为"[名字:描述]"文本,避免浪费过多token,后期考虑智能替换回url,   
rag召回重排序,  
文本知识库,支持大型文本文件进行上传,chunk+overlap+元数据 存入向量数据库  
会话摘要,  
支持对话中模型切换,