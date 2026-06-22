# MinIO 图片上传使用指南

## 📋 功能说明

本项目已集成 MinIO 对象存储，支持以下两种图片上传方式：

1. **直接上传图片文件**（multipart/form-data）
2. **从外部 URL 转存图片到 MinIO**（自动处理防盗链问题）

多模态对话接口已自动集成 MinIO 转存功能，当检测到外部图片 URL 时，会自动先转存到 MinIO，再发送给 AI 模型。

---

## 🚀 前置条件

### 1. 启动 MinIO 服务

确保 Docker Compose 中的 MinIO 服务正在运行：

```powershell
# 进入 docker 配置目录
cd doc\docker

# 启动所有服务（包括 MinIO）
docker-compose -f docker-compose-env.yml up -d

# 只启动 MinIO
docker-compose -f docker-compose-env.yml up -d minio
```

### 2. 验证 MinIO 是否启动成功

访问 MinIO 控制台：
- URL: http://localhost:9001
- 用户名: `minioadmin`
- 密码: `minio123`

---

## 📝 API 使用方法

### 方法一：直接上传图片文件

**接口地址：** `POST /api/file/upload/image`

**请求类型：** `multipart/form-data`

**示例（PowerShell）：**

```powershell
# 准备一个测试图片文件
$imagePath = "C:\Users\YourName\Pictures\test.jpg"

# 使用 Invoke-RestMethod 上传
$formData = @{
    file = Get-Item $imagePath
}

Invoke-RestMethod -Uri "http://localhost:8080/api/file/upload/image" -Method Post -Form $formData
```

**响应示例：**

```json
{
  "url": "http://localhost:9000/vllmpt-images/abc123def456.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&..."
}
```

---

### 方法二：从 URL 转存图片到 MinIO

**接口地址：** `POST /api/file/upload/from-url`

**请求类型：** `application/json`

**请求体：**

```json
{
  "imageUrl": "https://pica.zhimg.com/v2-d4ccab4e3096742097f22d93f3f1a434_r.jpg"
}
```

**PowerShell 示例：**

```powershell
$body = @{
    imageUrl = "https://pica.zhimg.com/v2-d4ccab4e3096742097f22d93f3f1a434_r.jpg"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/file/upload/from-url" -Method Post -Body $body -ContentType "application/json"
```

**响应示例：**

```json
{
  "url": "http://localhost:9000/vllmpt-images/xyz789abc012.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&..."
}
```

---

### 方法三：多模态对话（自动转存）

**接口地址：** `POST /api/chat/multimodal/single-image`

**请求类型：** `application/json`

**请求体：**

```json
{
  "text": "这件衣服怎么样？",
  "imageUrl": "https://pica.zhimg.com/v2-d4ccab4e3096742097f22d93f3f1a434_r.jpg"
}
```

**工作流程：**

1. 接收到请求
2. 检测到是外部 URL（非 localhost）
3. 自动下载图片并上传到 MinIO
4. 使用 MinIO 的公开 URL 调用 AI 模型
5. 返回 AI 回复

**PowerShell 示例：**

```powershell
$body = @{
    text = "这件衣服怎么样？"
    imageUrl = "https://pica.zhimg.com/v2-d4ccab4e3096742097f22d93f3f1a434_r.jpg"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/chat/multimodal/single-image" -Method Post -Body $body -ContentType "application/json"

Write-Host $response.response
```

---

## 🔧 配置说明

MinIO 配置位于 `application-dev.yml`：

```yaml
minio:
  endpoint: http://localhost:9000      # MinIO 服务端点
  access-key: minioadmin                # 访问密钥
  secret-key: minio123                  # 秘密密钥
  bucket-name: vllmpt-images            # Bucket 名称
  secure: false                         # 是否使用 HTTPS
```

---

## 💡 常见问题

### 1. MinIO 连接失败

**错误信息：** `Connect to localhost:9000 failed`

**解决方案：**
```powershell
# 检查 MinIO 容器状态
docker ps | findstr minio

# 如果未运行，启动 MinIO
docker-compose -f docker-compose-env.yml up -d minio

# 查看日志
docker logs vllmpt-minio
```

### 2. Bucket 不存在

系统会自动创建 Bucket，无需手动操作。如果需要手动创建：

```powershell
# 进入 MinIO 容器
docker exec -it vllmpt-minio sh

# 使用 mc 命令创建 bucket
mc alias set myminio http://localhost:9000 minioadmin minio123
mc mb myminio/vllmpt-images
```

### 3. 图片上传失败

**可能原因：**
- 图片大小超过限制（默认 10MB）
- 文件格式不支持
- MinIO 磁盘空间不足

**解决方案：**
```powershell
# 查看 MinIO 存储使用情况
docker exec -it vllmpt-minio du -sh /data

# 清理旧数据（谨慎操作）
docker exec -it vllmpt-minio rm -rf /data/vllmpt-images/*
```

### 4. 生成的 URL 有效期

默认生成的预签名 URL 有效期为 **7 天**。如需修改，编辑 [FileUploadService.java](file://D:\code\project\backend\vllmpt\src\main\java\org\albedo\vllmpt\file\service\FileUploadService.java)：

```java
.expiry(7 * 24 * 60 * 60) // 修改这里的数字（单位：秒）
```

---

## 🎯 最佳实践

### 1. 处理防盗链图片

对于知乎、微博等有防盗链的图片，**必须**先转存到 MinIO：

```json
// ✅ 正确做法：使用 from-url 接口先转存
POST /api/file/upload/from-url
{
  "imageUrl": "https://pica.zhimg.com/xxx.jpg"
}

// 然后使用返回的 MinIO URL 进行多模态对话
POST /api/chat/multimodal/single-image
{
  "text": "描述这张图片",
  "imageUrl": "http://localhost:9000/vllmpt-images/xxx.jpg?..."
}
```

或者直接使用多模态接口，它会自动处理转存。

### 2. 批量上传多张图片

```json
POST /api/chat/multimodal/multiple-images
{
  "text": "比较这几件衣服",
  "imageUrls": [
    "https://example.com/cloth1.jpg",
    "https://example.com/cloth2.jpg",
    "https://example.com/cloth3.jpg"
  ]
}
```

### 3. 本地开发调试

如果想跳过 MinIO 转存，直接使用本地图片 URL：

```json
{
  "text": "测试",
  "imageUrl": "http://localhost:9000/vllmpt-images/test.jpg"
}
```

系统会检测到是 localhost URL，不会重复转存。

---

## 📊 架构流程

```
用户请求
   │
   ├─→ POST /api/file/upload/image (直接上传)
   │       │
   │       └─→ MinIO 存储 → 返回公开 URL
   │
   ├─→ POST /api/file/upload/from-url (URL 转存)
   │       │
   │       ├─→ 下载外部图片
   │       ├─→ 上传到 MinIO
   │       └─→ 返回公开 URL
   │
   └─→ POST /api/chat/multimodal/single-image (多模态对话)
           │
           ├─→ 检测是否为外部 URL
           ├─→ 是 → 自动转存到 MinIO
           ├─→ 否 → 直接使用
           └─→ 调用 AI 模型 → 返回结果
```

---

## 🔍 调试技巧

### 查看应用日志

```powershell
# 实时查看日志
Get-Content logs\vllmpt-dev.log -Tail 50 -Wait

# 搜索图片上传相关日志
Select-String -Path logs\vllmpt-dev.log -Pattern "图片上传"
```

### 查看 MinIO 中的文件

访问 MinIO 控制台：http://localhost:9001

或使用命令行：

```powershell
docker exec -it vllmpt-minio ls /data/vllmpt-images
```

---

## ⚠️ 注意事项

1. **生产环境**务必修改默认密码（`minioadmin` / `minio123`）
2. 预签名 URL 有过期时间，不适合永久存储场景
3. 大图片上传可能耗时较长，建议前端增加加载提示
4. MinIO 存储空间有限，定期清理无用文件
5. 外部 URL 转存时会消耗服务器带宽和流量

---

## 📚 相关代码

- 配置类：[MinioConfig.java](file://D:\code\project\backend\vllmpt\src\main\java\org\albedo\vllmpt\file\config\MinioConfig.java)
- 服务类：[FileUploadService.java](file://D:\code\project\backend\vllmpt\src\main\java\org\albedo\vllmpt\file\service\FileUploadService.java)
- 控制器：[FileUploadController.java](file://D:\code\project\backend\vllmpt\src\main\java\org\albedo\vllmpt\file\controller\FileUploadController.java)
- 多模态助手：[MultimodalAssistant.java](file://D:\code\project\backend\vllmpt\src\main\java\org\albedo\vllmpt\ai\service\MultimodalAssistant.java)
