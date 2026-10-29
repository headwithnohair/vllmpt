package org.albedo.vllmpt.module.approval.biz.travel.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.module.approval.biz.travel.domain.entity.TravelExpense;
import org.albedo.vllmpt.module.approval.biz.travel.mapper.TravelExpenseMapper;
import org.albedo.vllmpt.module.approval.core.domain.entity.ApprovalInstance;
import org.albedo.vllmpt.module.approval.core.domain.enums.ApprovalInstanceStatus;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalBizHandler;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalContext;
import org.albedo.vllmpt.module.approval.core.spi.ApprovalForm;
import org.albedo.vllmpt.module.approval.core.spi.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 差旅费业务插件。
 * <p>
 * 申请阶段校验目的地、日期、金额、事由；审批结果只同步业务状态，
 * 金额分级由模板条件节点负责（amount > 5000 走财务、> 20000 走 CFO）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TravelBizHandler implements ApprovalBizHandler {

    public static final String BIZ_TYPE = "TRAVEL_EXPENSE";
    public static final String TEMPLATE_KEY = "travel_expense";

    private final TravelExpenseMapper travelExpenseMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public ValidationResult validate(ApprovalForm form, ApprovalContext ctx) {
        List<String> errors = new ArrayList<>();
        String destination = StrUtil.trim(form.getString("destination"));
        if (StrUtil.isBlank(destination)) {
            errors.add("目的地不能为空");
        }

        LocalDate start = parseDate(form.getString("startDate"));
        LocalDate end = parseDate(form.getString("endDate"));
        if (start == null) {
            errors.add("开始日期格式不正确，应为 yyyy-MM-dd");
        }
        if (end == null) {
            errors.add("结束日期格式不正确，应为 yyyy-MM-dd");
        }
        if (start != null && end != null) {
            if (end.isBefore(start)) {
                errors.add("结束日期不能早于开始日期");
            }
            if (start.isBefore(LocalDate.now())) {
                errors.add("不能申请过去的差旅");
            }
        }

        BigDecimal amount = parseAmount(form.get("estimatedAmount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("预计金额必须大于 0");
        }

        String reason = StrUtil.trim(form.getString("reason"));
        if (StrUtil.isBlank(reason)) {
            errors.add("出差事由不能为空");
        } else if (reason.length() > 500) {
            errors.add("出差事由不能超过 500 字");
        }

        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    @Override
    public Long createBizRecord(ApprovalForm form, ApprovalContext ctx) {
        LocalDate start = requireDate(form.getString("startDate"), "开始日期");
        LocalDate end = requireDate(form.getString("endDate"), "结束日期");
        BigDecimal amount = parseAmount(form.get("estimatedAmount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "预计金额必须大于 0");
        }

        LocalDateTime now = LocalDateTime.now();
        TravelExpense expense = new TravelExpense();
        expense.setApplicantId(ctx.applicantId());
        expense.setDestination(StrUtil.trim(form.getString("destination")));
        expense.setStartDate(start);
        expense.setEndDate(end);
        expense.setEstimatedAmount(amount);
        expense.setCurrency(StrUtil.blankToDefault(StrUtil.trim(form.getString("currency")), "CNY"));
        expense.setCostCenter(StrUtil.trim(form.getString("costCenter")));
        expense.setReason(StrUtil.trim(form.getString("reason")));
        expense.setInvoiceUrls(writeJson(parseAttachments(form.get("invoiceUrls"))));
        expense.setApprovalStatus(ApprovalInstanceStatus.RUNNING.name());
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);
        travelExpenseMapper.insert(expense);
        log.info("[差旅] 创建业务记录: id={}, applicant={}, amount={}",
                expense.getId(), ctx.applicantId(), amount);
        return expense.getId();
    }

    @Override
    public void bindInstance(Long bizId, Long instanceId) {
        travelExpenseMapper.update(null, new LambdaUpdateWrapper<TravelExpense>()
                .eq(TravelExpense::getId, bizId)
                .set(TravelExpense::getApprovalInstanceId, instanceId)
                .set(TravelExpense::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public Map<String, Object> extractFacts(ApprovalForm form, ApprovalContext ctx) {
        Map<String, Object> facts = new LinkedHashMap<>();
        LocalDate start = parseDate(form.getString("startDate"));
        LocalDate end = parseDate(form.getString("endDate"));
        BigDecimal amount = parseAmount(form.get("estimatedAmount"));
        List<String> invoices = parseAttachments(form.get("invoiceUrls"));
        facts.put("destination", StrUtil.trim(form.getString("destination")));
        facts.put("costCenter", StrUtil.trim(form.getString("costCenter")));
        if (start != null && end != null) {
            facts.put("startDate", start.toString());
            facts.put("endDate", end.toString());
            facts.put("days", ChronoUnit.DAYS.between(start, end) + 1);
        }
        if (amount != null) {
            // amount 为模板条件与旧版差旅规则使用的别名
            facts.put("amount", amount);
            facts.put("estimatedAmount", amount);
        }
        facts.put("invoiceCount", invoices.size());
        facts.put("hasInvoice", !invoices.isEmpty());
        return facts;
    }

    @Override
    public Object loadBizDetail(ApprovalInstance instance) {
        return findByInstance(instance);
    }

    @Override
    public void applyResult(ApprovalInstance instance, ApprovalInstanceStatus result) {
        TravelExpense expense = findByInstance(instance);
        if (expense == null) {
            log.warn("[差旅] 未找到业务记录，跳过结果回调: instanceId={}", instance.getId());
            return;
        }
        travelExpenseMapper.update(null, new LambdaUpdateWrapper<TravelExpense>()
                .eq(TravelExpense::getId, expense.getId())
                .set(TravelExpense::getApprovalStatus, result.name())
                .set(TravelExpense::getUpdatedAt, LocalDateTime.now()));
        log.info("[差旅] 结果回调: id={}, instanceId={}, status={}",
                expense.getId(), instance.getId(), result);
    }

    private TravelExpense findByInstance(ApprovalInstance instance) {
        if (instance.getBizId() != null && instance.getBizId() > 0) {
            TravelExpense byId = travelExpenseMapper.selectById(instance.getBizId());
            if (byId != null) {
                return byId;
            }
        }
        return travelExpenseMapper.selectOne(Wrappers.<TravelExpense>lambdaQuery()
                .eq(TravelExpense::getApprovalInstanceId, instance.getId())
                .last("LIMIT 1"));
    }

    private LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate requireDate(String value, String label) {
        LocalDate date = parseDate(value);
        if (date == null) {
            throw new BusinessException(400, label + "格式不正确，应为 yyyy-MM-dd");
        }
        return date;
    }

    private BigDecimal parseAmount(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> parseAttachments(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(item -> item != null && StrUtil.isNotBlank(String.valueOf(item)))
                    .map(String::valueOf)
                    .toList();
        }
        if (value instanceof String text) {
            if (StrUtil.isBlank(text)) {
                return List.of();
            }
            try {
                List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {
                });
                return parsed == null ? List.of() : parsed;
            } catch (Exception e) {
                return List.of(text);
            }
        }
        return List.of();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "差旅凭证序列化失败");
        }
    }
}
