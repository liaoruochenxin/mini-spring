package com.example.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.example.annotation.Bean;
import com.example.annotation.Component;
import com.example.exception.BeanDefinitionException;

import jakarta.annotation.Nullable;

/**
 * 判断是否存在 @Component ，不但要在当前类上查找，也要在当前类所有注解上，查找该注解是否有 @Component
 * 例如 SpringBoot 的 @Controller 上有 @Component
 * 因此，下面的方法可以递归查询注解
 */
public class ClassUtils {
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A a = target.getAnnotation(annoClass);
        // 递归遍历目标类型上所有注解 结束条件：一个类上再没有注解或者包名是java.lang.annotation
        for (Annotation anno : target.getAnnotations()) {
            Class<? extends Annotation> annoType = anno.annotationType();
            if (!annoType.getPackageName().equals("java.lang.annotation")) {
                A found = findAnnotation(annoType, annoClass);
                if (found != null) {
                    if (a != null) {
                        throw new BeanDefinitionException("Duplicate @" + annoClass.getSimpleName()
                                + " found on class " + target.getSimpleName());
                    }
                    a = found;
                }
            }
        }
        return a;
    }

    /**
     * get bean name by:
     * 
     * 获取带 @Component 注解的 Bean 的名称
     * <code>
     * @Component
     * public class Hello {}
     * </code>
     * @param clazz
     * @return
     */
    public static String getBeanName(Class<?> clazz) {
        String name = "";
        // 查找@Component
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            // Component exist:
            name = component.value();
        } else {
            // 未找到@Component, 继续在其他注解中查找@Component:
            for(Annotation anno : clazz.getAnnotations()) {
                if (findAnnotation(anno.annotationType(), null) != null) {
                    try {
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    } catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }
        if (name.isEmpty()) {
            // default name : "HelloWold => helloWorld"
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * Get bean name by:
     * 
     * 获取 @Bean 注解标注的 Bean 名称
     * <code>
     * @Bean
     * Hello createHello() {}
     * </code>
     */
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }

    /**
     * 获取指定类中无参的，有指定注解的方法
     * @param clazz 指定类
     * @param annoClass 指定注解类
     * @return 符合条件的方法
     */
    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        // try get declared method:
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods()).filter(
            m -> m.isAnnotationPresent(annoClass)
        ).map(
            m -> {
                if (m.getParameterCount() != 0) {
                    throw new BeanDefinitionException(String.format("Method '%s' with @%s must not have argument: %s ", m.getName(), annoClass.getSimpleName(), clazz.getName()));
                }
                return m;
            }).toList();
        if (ms.isEmpty()) {
            return null;
        }
        if (ms.size() == 1) {
            return ms.get(0);
        }
        throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s", annoClass.getSimpleName(), clazz.getName()));
    }
}
