package com.harmonycloud.zeus.bean;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统配置表
 * </p>
 *
 * @author skyview
 * @since 2021-05-07
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("system_config")
public class BeanSystemConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 配置名
     */
    @TableField("config_name")
    private String configName;

    @TableField("config_value")
    private String configValue;

    /**
     * 配置类型
     */
    @TableField("config_type")
    private String configType;

    /**
     * 创建人
     */
    @TableField("create_user")
    private String createUser;

    /**
     * 修改人
     */
    @TableField("update_user")
    private String updateUser;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

}
