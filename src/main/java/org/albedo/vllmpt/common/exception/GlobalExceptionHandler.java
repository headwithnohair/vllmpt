package org.albedo.vllmpt.common.exception;


import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.result.Result;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("参数校验异常: {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理所有未捕获的异常（兜底）
     */


    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception exception){
        log.error("系统异常: ", exception);
        return  Result.init(500,"内部异常",null);
    }
}
