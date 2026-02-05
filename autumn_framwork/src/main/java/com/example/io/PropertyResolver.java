package com.example.io;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * 配置文件读取
 * 
 * @author jw
 * @date 2026-2-3
 * @version 1.0
 */
public class PropertyResolver {

    Logger logger = LoggerFactory.getLogger(getClass());

    // 存储配置的map
    Map<String, String> properties = new HashMap<>();

    // 存储 Class -> Function 即类型转换目标类型 -> 转换方法
    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    // 读取环境变量的构造器
    public PropertyResolver(Properties properties) {
        // 存入环境变量
        this.properties.putAll(System.getenv());
        // 存入properties
        Set<String> names = properties.stringPropertyNames();
        for (String string : names) {
            this.properties.put(string, properties.getProperty(string));
        }

        // 初始化各个类型转换函数到 converters 中
        // String类型:
        converters.put(String.class, s -> s);
        // boolean类型:
        converters.put(boolean.class, s -> Boolean.parseBoolean(s));
        converters.put(Boolean.class, s -> Boolean.valueOf(s));
        // int类型:
        converters.put(int.class, s -> Integer.parseInt(s));
        converters.put(Integer.class, s -> Integer.valueOf(s));
        // 其他基本类型...
        // Date/Time类型:
        converters.put(LocalDate.class, s -> LocalDate.parse(s));
        converters.put(LocalTime.class, s -> LocalTime.parse(s));
        converters.put(LocalDateTime.class, s -> LocalDateTime.parse(s));
        converters.put(ZonedDateTime.class, s -> ZonedDateTime.parse(s));
        converters.put(Duration.class, s -> Duration.parse(s));
        converters.put(ZoneId.class, s -> ZoneId.of(s));
    }

    // 通过key查询配置项的方法
    @Nullable
    public String getProperty(String key) {
        PropertyExpr keyExpr = parsePropertyExpr(key);
        if (keyExpr != null) {
            if (keyExpr.defaultValue() != null) {
                // 带默认值的查询
                return getProperty(key, keyExpr.defaultValue());
            } else {
                // 不带默认值的查询
                return getProperty(key);
            }
        }
        // 普通key查询
        String value = this.properties.get(key);
        if (value != null) {
            return parseValue(value);
        }
        return value;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        // 获取对应Key的Value，若没有则返回默认值
        return value == null ? parseValue(defaultValue) : value;
    }

    /**
     * 类型转换的入口查询
     * 
     * @param <T>        返回值类型的泛型定义
     * @param key        需要查询的Key
     * @param targetType 返回值类型的class类
     * @return T 类型的Value
     */
    @Nullable
    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        return convert(targetType, value);
    }

    String parseValue(String value) {
        PropertyExpr expr = parsePropertyExpr(value);
        if (expr == null) {
            return value;
        }
        if (expr.defaultValue() != null) {
            return getProperty(expr.key(), expr.defaultValue());
        } else {
            return getRequiredProperty(expr.key());
        }
    }

    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        return Objects.requireNonNull(value, "Property '" + key + "' not found");
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType) {
        T value = getProperty(key, targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    @SuppressWarnings("unchecked")
    <T> T convert(Class<?> clazz, String value) {
        Function<String, Object> fn = this.converters.get(clazz);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported value type: " + clazz.getName());
        }
        // 由于Function的泛型使用了Object，所以需要强转为实际类型
        return (T) fn.apply(value);
    }

    // 解析成不可变类
    PropertyExpr parsePropertyExpr(String key) {
        // 先判断基本格式是否正确
        if (key.startsWith("${") && key.endsWith("}")) {
            // 是否存在默认值
            int n = key.indexOf(":");
            if (n == (-1)) {
                // 不存在默认值
                return new PropertyExpr(key.substring(2), null);
            } else {
                return new PropertyExpr(key.substring(2, n), key.substring(n + 1));
            }
        }
        return null;
    }

    String notEmpty(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        return key;
    }
}

// 一个用于存储解析后配置信息(key, defaultValue)的不可变类
record PropertyExpr(String key, String defaultValue) {
}