package com.dynamic.route.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dynamic.route.entity.AppDefinitionEntity;
import com.dynamic.route.entity.PluginDefinitionEntity;
import com.dynamic.route.entity.RouteDefinitionEntity;
import com.dynamic.route.entity.RoutePluginBindingEntity;
import com.dynamic.route.entity.RouteTargetEntity;
import com.dynamic.route.mapper.AppDefinitionMapper;
import com.dynamic.route.mapper.PluginDefinitionMapper;
import com.dynamic.route.mapper.RouteDefinitionMapper;
import com.dynamic.route.mapper.RoutePluginBindingMapper;
import com.dynamic.route.mapper.RouteTargetMapper;
import com.dynamic.route.model.AppDefinition;
import com.dynamic.route.model.FailStrategy;
import com.dynamic.route.model.PathMatchType;
import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.PluginExecutionPhase;
import com.dynamic.route.model.PluginPhase;
import com.dynamic.route.model.RouteDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.model.RouteTarget;
import com.dynamic.route.model.TargetType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class RouteConfigurationRepository {

    private final AppDefinitionMapper appMapper;
    private final RouteTargetMapper targetMapper;
    private final RouteDefinitionMapper routeMapper;
    private final PluginDefinitionMapper pluginMapper;
    private final RoutePluginBindingMapper bindingMapper;

    public RouteConfigurationRepository(
        AppDefinitionMapper appMapper,
        RouteTargetMapper targetMapper,
        RouteDefinitionMapper routeMapper,
        PluginDefinitionMapper pluginMapper,
        RoutePluginBindingMapper bindingMapper
    ) {
        this.appMapper = appMapper;
        this.targetMapper = targetMapper;
        this.routeMapper = routeMapper;
        this.pluginMapper = pluginMapper;
        this.bindingMapper = bindingMapper;
    }

    public List<AppDefinition> findActiveApps() {
        return appMapper.selectList(
            new LambdaQueryWrapper<AppDefinitionEntity>()
                .eq(AppDefinitionEntity::getStatus, "ACTIVE")
        ).stream().map(this::toAppDefinition).toList();
    }

    public List<RouteTarget> findActiveTargets() {
        return targetMapper.selectList(
            new LambdaQueryWrapper<RouteTargetEntity>()
                .eq(RouteTargetEntity::getStatus, "ACTIVE")
        ).stream().map(this::toRouteTarget).toList();
    }

    public List<RouteDefinition> findActiveRoutes() {
        return routeMapper.selectList(
            new LambdaQueryWrapper<RouteDefinitionEntity>()
                .eq(RouteDefinitionEntity::getStatus, "ACTIVE")
                .orderByAsc(RouteDefinitionEntity::getRouteOrder)
                .orderByAsc(RouteDefinitionEntity::getId)
        ).stream().map(this::toRouteDefinition).toList();
    }

    public List<PluginDefinition> findActivePlugins() {
        return pluginMapper.selectList(
            new LambdaQueryWrapper<PluginDefinitionEntity>()
                .eq(PluginDefinitionEntity::getStatus, "ACTIVE")
        ).stream().map(this::toPluginDefinition).toList();
    }

    public List<RoutePluginBinding> findEnabledBindings() {
        return bindingMapper.selectList(
            new LambdaQueryWrapper<RoutePluginBindingEntity>()
                .eq(RoutePluginBindingEntity::getEnabled, 1)
                .orderByAsc(RoutePluginBindingEntity::getRouteCode)
                .orderByAsc(RoutePluginBindingEntity::getPluginPhase)
                .orderByAsc(RoutePluginBindingEntity::getSortOrder)
                .orderByAsc(RoutePluginBindingEntity::getId)
        ).stream().map(this::toRoutePluginBinding).toList();
    }

    // -----------------------------------------------------------------------
    // Entity → Domain Record 转换
    // -----------------------------------------------------------------------

    private AppDefinition toAppDefinition(AppDefinitionEntity e) {
        return new AppDefinition(e.getId(), e.getAppCode(), e.getAppName(),
            e.getStatus(), e.getRemark(), toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private RouteTarget toRouteTarget(RouteTargetEntity e) {
        return new RouteTarget(e.getId(), e.getTargetCode(), e.getTargetName(),
            TargetType.fromValue(e.getTargetType()), e.getEndpointUri(), e.getComponentName(),
            e.getDatasourceName(), e.getOperationType(), e.getConfigJson(), e.getSecretRef(),
            e.getStatus(), e.getVersion(), e.getRemark(),
            toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private RouteDefinition toRouteDefinition(RouteDefinitionEntity e) {
        return new RouteDefinition(e.getId(), e.getRouteCode(), e.getAppCode(), e.getRouteName(),
            e.getEntryProtocol(), e.getRequestPath(), PathMatchType.fromValue(e.getPathMatchType()),
            e.getRequestMethod(), e.getRequestFormat(), e.getContentType(), e.getAcceptType(),
            e.getRouteOrder(), e.getTargetCode(), e.getTimeoutMs(),
            e.getRetryTimes() != null ? e.getRetryTimes() : 0,
            e.getStatus(), e.getVersion() != null ? e.getVersion() : 1L,
            e.getRemark(), e.getCreatedBy(), e.getUpdatedBy(),
            toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private PluginDefinition toPluginDefinition(PluginDefinitionEntity e) {
        return new PluginDefinition(e.getId(), e.getPluginCode(), e.getPluginName(),
            PluginPhase.fromValue(e.getPluginPhase()), e.getPluginScope(), e.getBeanName(),
            e.getPluginClass(), e.getConfigSchemaJson(), e.getStatus(), e.getRemark(),
            toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private RoutePluginBinding toRoutePluginBinding(RoutePluginBindingEntity e) {
        return new RoutePluginBinding(e.getId(), e.getRouteCode(), e.getPluginCode(),
            PluginExecutionPhase.fromValue(e.getPluginPhase()),
            e.getSortOrder() != null ? e.getSortOrder() : 0,
            e.getEnabled() != null && e.getEnabled() == 1,
            FailStrategy.fromValue(e.getFailStrategy()), e.getPluginConfigJson(),
            toInstant(e.getCreatedAt()), toInstant(e.getUpdatedAt()));
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? Instant.now() : ldt.atZone(ZoneId.systemDefault()).toInstant();
    }
}
