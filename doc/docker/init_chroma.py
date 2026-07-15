import chromadb

# 1. 连接到 Docker Chroma
client = chromadb.HttpClient(host='localhost', port=8000)

# 2. 【关键】彻底删除旧的 384 维集合（如果存在的话）
try:
    client.delete_collection(name="vllmpt_knowledge_base")
    print("🗑️ 删除旧的集合")
except Exception as e:
    print("ℹ️ 旧集合不存在，继续创建...")

# 3. 创建全新的空集合（⚠️ 不要加 metadata 参数，新版 API 不支持了）
collection = client.create_collection(name="vllmpt_knowledge_base")
print("📦 空集合创建成功")

# 4. 写入 1024 维的假向量
# Chroma 会自动识别这是 1024 维，并将该集合的维度永久锁定为 1024！
dummy_embedding = [[0.1] * 1024]
collection.add(
    documents=["这是一条用于初始化集合的测试数据"],
    embeddings=dummy_embedding,
    ids=["init_001"]
)
print("📝 写入 1024 维假向量成功，维度已自动锁定")

# 5. 删掉假数据，保持集合干净
collection.delete(ids=["init_001"])

print("✅ 完美！1024 维集合初始化并清理完毕！")
print(f"📊 当前集合内数据量: {collection.count()}")