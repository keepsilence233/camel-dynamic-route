package com.dynamic.route.executor;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TargetExecutorRegistry {

    private final Map<String, TargetExecutor> executorsByType;

    // Spring 自动注入全部 TargetExecutor bean；以 supportType() 重新建索引，运行时按 target 类型查找
    public TargetExecutorRegistry(Map<String, TargetExecutor> executorsByBeanName) {
        this.executorsByType = executorsByBeanName.values().stream()
            .collect(Collectors.toUnmodifiableMap(TargetExecutor::supportType, Function.identity()));
    }

    public TargetExecutor require(String targetType) {
        TargetExecutor executor = executorsByType.get(targetType.toLowerCase());
        if (executor == null) {
            throw new IllegalStateException("Unsupported target type: " + targetType);
        }
        return executor;
    }
}
