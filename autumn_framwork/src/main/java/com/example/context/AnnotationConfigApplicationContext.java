package com.example.context;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.annotation.ComponentScan;
import com.example.exception.NoUniqueBeanDefinitionException;
import com.example.io.PropertyResolver;
import com.example.io.ResourceResolver;
import com.example.utils.ClassUtils;

import jakarta.annotation.Nullable;

public class AnnotationConfigApplicationContext {

    Logger logger = LoggerFactory.getLogger(getClass());
    Map<String, BeanDefinition> beans;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        // 扫描获取所有 Bean 的 Class 类型
        scanFor
    // 根据 name 查找 BeanDefinition，如果不存在返回 null
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 从 beans 中获取对应类型的 Bean 列表
     * 
     * @param type Java类型
     * @return Bean 列表
     */
    List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
                // 按类型过滤
                .filter(def -> type.isAssignableFrom(type))
                // 排序
                .sorted().toList();
    }

    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if (defs.isEmpty()) { // 未找到对应类型的Bean
            return null;
        }
        if (defs.size() == 1) { // 找到唯一 Bean
            return defs.get(0);
        }
        // 多于一个时，查找@Primary:
        List<BeanDefinition> list = defs.stream().filter(def -> def.isPrimary()).toList();
        if (list.size() == 1) { // 一个 @Primary
            return list.get(0);
        }
        if (list.isEmpty()) { // 不存在@Primary
            throw new NoUniqueBeanDefinitionException(
                    String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else { // @Primary不唯一
            throw new NoUniqueBeanDefinitionException(String
                    .format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }

    /**
     * 扫描指定包下的类名
     * @param configClass 配置类 Class
     * @return Class 名字
     */
    Set<String> scanForClassName(Class<?> configClass) {
        // 获取@ComponentScan注解
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        // 获取注解配置的 package 名字，未配置则默认当前类所在 package 
        String[] scanPackages = scan == null || scan.value().length == 0 ? new String[] {configClass.getPackage().getName()} : scan.value();

        Set<String> classNameSet = new HashSet<>();
        // 依次扫描所有包
        for (String pkg : scanPackages) {
            logger.atDebug().log("scan package: {}", pkg);
            // 扫描一个包
            var rr = new ResourceResolver(pkg);
            List<String> ClassList = rr.scan(res -> {
                // 遇到以 .class 结尾的文件，就将其转换为 Class 全名:
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
        }
    }
}
