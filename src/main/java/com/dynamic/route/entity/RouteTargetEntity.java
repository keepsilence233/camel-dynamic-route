package com.dynamic.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("route_target")
public class RouteTargetEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetCode;
    private String targetName;
    private String targetType;
    private String endpointUri;
    private String componentName;
    private String datasourceName;
    private String operationType;
    private String configJson;
    private String secretRef;
    private String status;
    private Long version;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
