package org.albedo.vllmpt.module.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.file.model.entity.FileParseTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大文件解析任务台账表数据访问接口。
 */
@Mapper
public interface FileParseTaskMapper extends BaseMapper<FileParseTask> {
}
