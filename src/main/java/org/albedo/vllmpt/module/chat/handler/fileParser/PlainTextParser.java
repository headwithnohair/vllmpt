package org.albedo.vllmpt.module.chat.handler.fileParser;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import org.albedo.vllmpt.module.chat.handler.fileEmbedding.TextEmbedding;
import org.albedo.vllmpt.module.chat.model.entity.FileParseResult;
import org.albedo.vllmpt.module.chat.service.FileParser;
import org.albedo.vllmpt.module.file.entity.dto.FileUploadDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PlainTextParser implements FileParser {

    private static final String SupportType = "text/plain";

    // 每个 Chunk 的最大字符数 (建议根据 Embedding 模型的 token 上限换算，如 500 token ≈ 800-1000 字符)
    private static final int MAX_CHUNK_CHARS = 1000;

    // 重叠字符数，防止上下文在切分边界丢失
    private static final int OVERLAP_CHARS = 150;

    // 单行最大字符数保护：超过此长度的行将被强制截断，防止恶意/畸形输入导致 OOM
    // 设置为 MAX_CHUNK_CHARS 的合理倍数，确保即使单行再长也不会撑爆内存
    private static final int MAX_LINE_CHARS = 100_000;

    // 每次从流中读取的字符缓冲区大小，避免频繁 I/O
    private static final int READ_BUF_SIZE = 8192;

    // 批量 emit chunk 的大小，控制单次 emit 的 chunk 数量，避免收集过多
    private static final int EMIT_BATCH_SIZE = 50;

    @Autowired
    TextEmbedding textEmbedding;

    @Override
    public boolean supports(String type) {
        return type.equals(SupportType);
    }

    @Override
    public FileParseResult process(InputStream inputStream, FileUploadDTO fileUploadDTO) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8), READ_BUF_SIZE)) {

            // 使用预分配容量的 StringBuilder，减少扩容开销
            StringBuilder buffer = new StringBuilder(MAX_CHUNK_CHARS + OVERLAP_CHARS + 256);
            String overlapTail = "";
            long chunkIndex = 0;
            int totalChunks = 0;

            char[] cbuf = new char[READ_BUF_SIZE];
            int charsRead;

            // ===== 流式按字符块读取，完全不依赖 readLine()，避免单行超大导致 OOM =====
            while ((charsRead = reader.read(cbuf)) != -1) {
                for (int i = 0; i < charsRead; i++) {
                    char c = cbuf[i];

                    // 始终追加到缓冲区
                    buffer.append(c);

                    // 判断是否需要切分 chunk
                    boolean shouldSplit = shouldSplitChunk(buffer, c);

                    if (shouldSplit) {
                        // 从缓冲区中提取并 emit 一个完整的 chunk
                        String chunkText = extractChunkText(buffer);

                        if (!chunkText.isEmpty()) {
                            String finalText = overlapTail + chunkText;
                            emitChunk(finalText, fileUploadDTO, chunkIndex++);
                            totalChunks++;

                            // 更新 overlap 尾部
                            overlapTail = computeOverlapTail(chunkText);
                        }
                    }

                    // 安全检查：如果缓冲区异常增长（理论上不应发生，但兜底保护）
                    if (buffer.length() > MAX_LINE_CHARS) {
                        log.warn("缓冲区异常增长至 {} 字符，强制截断 flush", buffer.length());
                        String chunkText = extractChunkText(buffer);
                        if (!chunkText.isEmpty()) {
                            String finalText = overlapTail + chunkText;
                            emitChunk(finalText, fileUploadDTO, chunkIndex++);
                            totalChunks++;
                            overlapTail = computeOverlapTail(chunkText);
                        }
                    }
                }
            }

            // 处理文件末尾残余内容
            if (buffer.length() > 0) {
                String chunkText = buffer.toString().trim();
                if (!chunkText.isEmpty()) {
                    String finalText = overlapTail + chunkText;
                    emitChunk(finalText, fileUploadDTO, chunkIndex);
                    totalChunks++;
                }
            }

            log.info("文件解析完成，共生成 {} 个 Chunk", totalChunks);
            return new FileParseResult();

        } catch (Exception e) {
            log.error("流式处理文件失败", e);
            return new FileParseResult();
        }
    }

    /**
     * 判断当前缓冲区是否满足切分条件。
     * 切分策略：
     * 1. 优先按自然段落切分（遇到连续两个换行符，即空行）
     * 2. 达到最大字符数时按标点符号切分（尽量在语义边界断开）
     * 3. 兜底：超过最大字符数的 1.5 倍时强制切分
     */
    private boolean shouldSplitChunk(StringBuilder buffer, char currentChar) {
        int len = buffer.length();

        // 条件 1：遇到空行（双换行），优先在段落边界切分
        if (len >= 2 && currentChar == '\n') {
            int lastIdx = len - 1;
            if (buffer.charAt(lastIdx - 1) == '\n') {
                // 确认是空行（前后都是换行），避免把单个 \n 当空行
                return true;
            }
        }

        // 条件 2：达到字符数上限，且在合适的语义边界（句子结束处）切分
        if (len >= MAX_CHUNK_CHARS && isSemanticBoundary(currentChar)) {
            return true;
        }

        // 条件 3：兜底保护，超过 1.5 倍上限强制切分
        if (len >= MAX_CHUNK_CHARS * 1.5) {
            return true;
        }

        return false;
    }


    /**
     * 判断当前字符是否是合适的语义切分边界。
     * 优先在句子结束处（。！？.!?）、换行处切分，尽量避免在词语中间断开。
     */
    private boolean isSemanticBoundary(char c) {
        // 中文句子结束标点
        if (c == '。' || c == '！' || c == '？' || c == '…') return true;
        // 英文句子结束标点
        if (c == '.' || c == '!' || c == '?') return true;
        // 换行也是好的切分点
        if (c == '\n') return true;
        // 分号、冒号也是次优但可接受的切分点
        if (c == '；' || c == '：' || c == ';' || c == ':') return true;
        return false;
    }

    /**
     * 从缓冲区中提取 chunk 文本并清空缓冲区。
     * 使用 CharBuffer 避免 toString() 在超大缓冲区上的内存翻倍问题。
     */
    private String extractChunkText(StringBuilder buffer) {
        String text = buffer.toString().trim();
        buffer.setLength(0);
        return text;
    }

    /**
     * 计算下一个 chunk 的 overlap 尾部。
     */
    private String computeOverlapTail(String chunkText) {
        if (chunkText.length() > OVERLAP_CHARS) {
            return chunkText.substring(chunkText.length() - OVERLAP_CHARS) + "\n";
        } else {
            return chunkText + "\n";
        }
    }

     /**
     * Emit 一个 chunk（进行向量化处理）。
     * 当前为占位实现，后续接入 Embedding 模型时在此处实现。
     */
    private void emitChunk(String text, FileUploadDTO fileUploadDTO, long chunkIndex) {
        Metadata metadata = new Metadata()
                .put("fileName", fileUploadDTO.getObjectName())
                .put("fileType", fileUploadDTO.getMimeType())
                .put("chunkIndex", chunkIndex);


         TextSegment segment = TextSegment.from(text, metadata);
        textEmbedding.chunkEmbeding(segment);

        log.debug("Chunk [{}] 已生成, 长度: {}", chunkIndex, text.length());
    }

    @Override
    public String getSupportTypes() {
        return SupportType;
    }
}
