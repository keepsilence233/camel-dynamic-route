package com.dynamic.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("route_plugin_binding")
public class RoutePluginBindingEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeCode;
    private String pluginCode;
    private String pluginPhase;
    private Integer sortOrder;
    private Integer enabled;
    private String failStrategy;
    private String pluginConfigJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
