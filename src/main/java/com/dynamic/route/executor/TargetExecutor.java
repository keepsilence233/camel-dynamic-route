package com.dynamic.route.executor;

import com.dynamic.route.engine.RouteContext;

public interface TargetExecutor {
    String supportType();

    Object execute(RouteContext context);
}
