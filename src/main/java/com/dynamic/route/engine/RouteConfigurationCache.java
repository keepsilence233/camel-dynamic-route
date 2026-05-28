package com.dynamic.route.engine;

import com.dynamic.route.model.PluginDefinition;
import com.dynamic.route.model.RoutePluginBinding;
import com.dynamic.route.repository.RouteConfigurationRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RouteConfigurationCache {

    private static final Logger log = LoggerFactory.getLogger(RouteConfigurationCache.class);

    // AtomicReference 保证并发读者始终拿到完整快照，不会在刷新过程中看到半构造状态
    private final AtomicReference<RouteConfigurationSnapshot> snapshotReference;
    private final RouteConfigurationRepository repository;

    public RouteConfigurationCache(RouteConfigurationRepository repository) {
        this.repository = repository;
        RouteConfigurationSnapshot initial = repository == null ? emptySnapshot() : loadSnapshot();
        this.snapshotReference = new AtomicReference<>(initial);
        log.info("Cache initialized: routes={} targets={} plugins={}",
            initial.routes().size(), initial.targetsByCode().size(), initial.pluginsByCode().size());
    }

    public RouteConfigurationSnapshot currentSnapshot() {
        return snapshotReference.get();
    }

    // fixedDelay 而非 fixedRate：确保上次 DB 查询结束后再开始下次，避免查询耗时超过间隔时出现并发刷新
    @Scheduled(fixedDelayString = "${route.cache.refresh-ms}")
    public void refresh() {
        if (repository == null) {
            return;
        }
        RouteConfigurationSnapshot snapshot = loadSnapshot();
        snapshotReference.set(snapshot);
        log.info("Cache refreshed: routes={} targets={} plugins={}",
            snapshot.routes().size(), snapshot.targetsByCode().size(), snapshot.pluginsByCode().size());
    }

    // protected 供测试子类重写，以便在无真实 DB 的情况下注入初始数据
    protected RouteConfigurationSnapshot emptySnapshot() {
        return new RouteConfigurationSnapshot(java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.List.of());
    }

    private RouteConfigurationSnapshot loadSnapshot() {
        List<PluginDefinition> pluginDefinitions = repository.findActivePlugins();
        List<RoutePluginBinding> bindings = repository.findEnabledBindings();
        return new RouteConfigurationSnapshot(
            repository.findActiveTargets().stream()
                .collect(Collectors.toUnmodifiableMap(it -> it.targetCode(), Function.identity())),
            pluginDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(it -> it.pluginCode(), Function.identity())),
            bindings.stream()
                .collect(Collectors.groupingBy(RoutePluginBinding::routeCode, Collectors.toUnmodifiableList())),
            List.copyOf(repository.findActiveRoutes())
        );
    }
}
