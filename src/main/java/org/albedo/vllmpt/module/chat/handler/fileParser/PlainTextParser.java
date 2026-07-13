package org.albedo.vllmpt.module.chat.handler.fileParser;

import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PlainTextParser implements FileParser {


    private static final String SupportType ="text/plain";
    private static final int MAX_CHUNK_CHARS = 1000;
    // 设定重叠字符数 (Overlap)，防止上下文在切分边界丢失
    private static final int OVERLAP_CHARS = 150;
    @Override
    public boolean supports(String type) {
        return type.equals(SupportType);
    }


    // 【参数配置】
    // 设定每个 Chunk 的最大字符数阈值 (建议根据你的 Embedding 模型换算，比如 500 token 约等于 800-1000 字符)

    @Override
    public FileParseResult process(InputStream inputStream, FileUploadDTO fileUploadDTO) {

        // 1. 使用 BufferedReader 按行读取，彻底解决 InputStream.read(byte[]) 导致的 UTF-8 中文截断乱码问题
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder currentChunkBuffer = new StringBuilder();
            long chunkIndex = 0L;
            String line;
            String previousChunkTail = ""; // 用于实现 Overlap (重叠上下文)

            // 2. 流式逐行读取
            while ((line = reader.readLine()) != null) {
                // 将读取到的行追加到缓冲区，并手动补上换行符以保留段落结构
                currentChunkBuffer.append(line).append("\n");

                // 【核心切分逻辑】
                // 条件 A：遇到空行，说明一个自然段落结束了
                boolean isParagraphEnd = line.trim().isEmpty();
                // 条件 B：缓冲区字符数达到了设定的上限（防止遇到几千字不换行的超级大段落导致内存暴涨）
                boolean isSizeLimitReached = currentChunkBuffer.length() >= MAX_CHUNK_CHARS;

                // 如果满足切分条件，且缓冲区有实际内容
                if ((isParagraphEnd || isSizeLimitReached) && !currentChunkBuffer.isEmpty()) {

                    String fullChunkText = currentChunkBuffer.toString().trim();

                    if (!fullChunkText.isEmpty()) {
                        // 3. 构建当前 Chunk 专属的 Metadata (每个 Chunk 都有独立的元数据)
                        Metadata chunkMetadata = new Metadata()
                                .put("fileName", fileUploadDTO.getObjectName())
                                .put("fileType", fileUploadDTO.getMimeType())
                                .put("chunkIndex", chunkIndex); // 记录这是第几个 Chunk
                        // .put("author", fileUploadDTO.getAuthor()) // 有其他元数据继续 put

                        // 4. 处理 Overlap (重叠上下文)：把上一个 chunk 的尾巴拼到当前 chunk 头部
                        String finalTextForVectorization = previousChunkTail + fullChunkText;

                        // ==========================================
                        // TODO: 在这里调用你的 Embedding 模型和向量数据库
                        // TextSegment segment = TextSegment.from(finalTextForVectorization, chunkMetadata);
                        // embeddingStore.add(segment);
                        // ==========================================

                        log.info("成功处理 Chunk [{}], 最终长度: {}", chunkIndex, finalTextForVectorization.length());

                        // 5. 截取当前 Chunk 的尾部，作为下一个 Chunk 的 Overlap 头部
                        if (fullChunkText.length() > OVERLAP_CHARS) {
                            previousChunkTail = fullChunkText.substring(fullChunkText.length() - OVERLAP_CHARS) + "\n";
                        } else {
                            previousChunkTail = fullChunkText + "\n";
                        }

                        chunkIndex++;
                    }

                    // 6. 清空缓冲区，准备读取下一段
                    currentChunkBuffer.setLength(0);
                }
            }

            // 7. 处理文件末尾不足一个 Chunk 的剩余内容 (扫尾工作)
            if (!currentChunkBuffer.isEmpty()) {
                String finalChunkText = currentChunkBuffer.toString().trim();
                if (!finalChunkText.isEmpty()) {
                    Metadata finalMetadata = new Metadata()
                            .put("fileName", fileUploadDTO.getObjectName())
                            .put("fileType", fileUploadDTO.getMimeType())
                            .put("chunkIndex", chunkIndex);

                    String finalText = previousChunkTail + finalChunkText;

                    // TODO: 向量化并存储最后一个 Chunk
                    // TextSegment segment = TextSegment.from(finalText, finalMetadata);
                    // embeddingStore.add(segment);

                    log.info("处理最后一个 Chunk [{}]", chunkIndex);
                    chunkIndex++;
                }
            }

            return new FileParseResult();

        } catch (Exception e) {
            log.error("流式处理文件失败", e);
            return new FileParseResult();
        }
    }


    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
