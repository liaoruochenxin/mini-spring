package com.example.utils;

import java.lang.annotation.Annotation;

import com.example.exception.BeanDefinitionException;

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
}
