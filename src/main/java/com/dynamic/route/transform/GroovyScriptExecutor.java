package com.dynamic.route.transform;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Groovy 脚本执行器。
 *
 * 设计要点：
 *   - 每个唯一脚本文本只编译一次，编译后的 Class 缓存在 scriptCache 中
 *   - 每次执行 new 一个 Script 实例（Script 实例非线程安全），绑定独立的 Binding
 *   - 注意：脚本来自数据库配置，视为受信任输入；生产环境如有安全顾虑，请配合沙箱策略
 *
 * 脚本可用变量：
 *   value — 当前字段值（已经过 type 转换和 default 填充）
 *   ctx   — 该字段所在的父 Map（可读取同级字段）
 */
@Component
public class GroovyScriptExecutor {

    private final ConcurrentHashMap<String, Class<? extends Script>> scriptCache = new ConcurrentHashMap<>();
    private final GroovyClassLoader classLoader = new GroovyClassLoader();

    public Object execute(String scriptText, Object value, Map<String, Object> ctx) {
        Class<? extends Script> scriptClass = scriptCache.computeIfAbsent(scriptText, this::compile);
        try {
            Binding binding = new Binding();
            binding.setVariable("value", value);
            binding.setVariable("ctx", ctx);
            Script script = scriptClass.getDeclaredConstructor().newInstance();
            script.setBinding(binding);
            return script.run();
        } catch (Exception e) {
            throw new IllegalStateException("Groovy script execution failed: [" + scriptText + "]", e);
        }
    }

    /** 脚本为 null 或空时直接透传，不执行 */
    public Object maybeExecute(String scriptText, Object value, Map<String, Object> ctx) {
        if (scriptText == null || scriptText.isBlank()) {
            return value;
        }
        return execute(scriptText, value, ctx);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Script> compile(String scriptText) {
        return (Class<? extends Script>) classLoader.parseClass(scriptText);
    }

    @PreDestroy
    public void close() {
        try {
            classLoader.close();
        } catch (IOException ignored) {
        }
    }
}
