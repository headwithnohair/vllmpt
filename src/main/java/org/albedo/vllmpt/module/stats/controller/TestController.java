package org.albedo.vllmpt.module.stats.controller;

import lombok.extern.slf4j.Slf4j;
import org.albedo.vllmpt.common.exception.BusinessException;
import org.albedo.vllmpt.common.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/stat")
public class TestController {

    @PostMapping("/errorTest")
    public Result<String> errorTest(@RequestBody String code) {
        log.info(code);
        return Result.init(500, "pp", null);
    }

    @PostMapping("/errorTest2")
    public Result<String> errorTestTo() {
        throw new BusinessException(500, "故意报错");
    }
}
