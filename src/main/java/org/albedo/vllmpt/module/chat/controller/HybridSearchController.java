package org.albedo.vllmpt.module.chat.controller;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.albedo.vllmpt.module.chat.es.HybridSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 混合检索接口（场景4 联调入口）：BM25 + 向量 → RRF → Top-K。
 * <p>
 * 调用示例（Postman 选 raw → text，body 直接填查询词）：
 * <pre>
 * POST /api/hybrid/search?topK=5
 * 智谱清言是什么
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/hybrid")
@RequiredArgsConstructor
public class HybridSearchController {

    private final HybridSearchService hybridSearchService;

    /**
     * 混合检索。
     *
     * @param query 查询词（请求体 raw text）
     * @param topK  返回条数，默认 5
     * @return 融合后的分块文本列表（按 RRF 分数降序）
     */
    @PostMapping("/search")
    public Result<List<String>> search(@RequestBody String query,
                                       @RequestParam(value = "topK", defaultValue = "5") int topK) {
        if (StrUtil.isBlank(query)) {
            throw new BusinessException(400, "查询词不能为空");
        }
        return Result.success(hybridSearchService.hybridSearch(query.trim(), topK));
    }
}
