package com.dynamic.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("route_definition")
public class RouteDefinitionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routeCode;
    private String appCode;
    private String routeName;
    private String entryProtocol;
    private String requestPath;
    private String pathMatchType;
    private String requestMethod;
    private String requestFormat;
    private String contentType;
    private String acceptType;
    private Integer routeOrder;
    private String targetCode;
    private Integer timeoutMs;
    private Integer retryTimes;
    private String status;
    private Long version;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
