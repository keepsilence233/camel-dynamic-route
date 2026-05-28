package com.dynamic.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("plugin_definition")
public class PluginDefinitionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String pluginCode;
    private String pluginName;
    private String pluginPhase;
    private String pluginScope;
    private String beanName;
    private String pluginClass;
    private String configSchemaJson;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
