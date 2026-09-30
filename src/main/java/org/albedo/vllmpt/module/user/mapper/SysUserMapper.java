package org.albedo.vllmpt.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.albedo.vllmpt.module.user.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问接口。
 * <p>
 * 只继承 BaseMapper，不定义自定义方法；后续需要复杂查询时，
 * 可以加 {@code @Select} 或把 XML 放到 {@code src/main/resources/mapper/} 下
 * （application.yml 已配置 mapper-locations: classpath*:mapper/** /*.xml）。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
