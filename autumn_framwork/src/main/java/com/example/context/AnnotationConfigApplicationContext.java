package com.example.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.annotation.Bean;
import com.example.annotation.Component;
import com.example.annotation.ComponentScan;
import com.example.annotation.Configuration;
import com.example.annotation.Import;
import com.example.annotation.Order;
import com.example.annotation.Primary;
import com.example.exception.BeanCreationException;
import com.example.exception.BeanDefinitionException;
import com.example.exception.BeanNotOfRequiredTypeException;
import com.example.exception.NoUniqueBeanDefinitionException;
import com.example.io.PropertyResolver;
import com.example.io.ResourceResolver;
import com.example.utils.ClassUtils;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final Map<String, BeanDefinition> beans;
    protected final PropertyResolver propertyResolver;


    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;
        
        // 扫描获取所有 Bean 的 Class 类型
        final Set<String> beanClassNames = scanForClassName(configClass);
        // 创建 Bean 的定义
        this.beans = createBeanDefinitions(beanClassNames);
    }

    /**
     * 根据扫描的ClassName创建BeanDefinition
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        Map<String, BeanDefinition> defs = new HashMap<>();
        for (String className : classNameSet) {
            // 获取Class:
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }
            // 判断是否标注@Component
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if (component != null) {
                logger.atDebug().log("found component: {}", clazz.getName());
                int mod = clazz.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be abstract");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be private");
                }

                String beanName = ClassUtils.getBeanName(clazz);
                var def = new BeanDefinition(beanName, clazz, getSuitableConstructor(clazz), getOrder(clazz), clazz.isAnnotationPresent(Primary.class), 
                    // named init / destroy method
                    null, 
                    null, 
                    ClassUtils.findAnnotationMethod(clazz, PostConstruct.class), 
                    ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));
                addBeanDefinitions(defs, def);
                logger.atDebug().log("define bean: {}", def);

                // 如果当前类是配置类时的处理
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if (configuration != null) {
                    scanFactoryMethods(beanName, clazz, defs);
                }
            }
        }
        return defs;
    }

    /**
     * 扫描带 @Bean 注解的工厂方法
     * @param factoryBeanName
     * @param clazz
     * @param defs
     */
    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs) {
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract");
                }
                if (Modifier.isFinal(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if (beanClass.isPrimitive()) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }
                var def = new BeanDefinition(ClassUtils.getBeanName(method), beanClass, factoryBeanName, method, getOrder(method), 
                    method.isAnnotationPresent(Primary.class), 
                    // init method
                    bean.initMethod().isEmpty() ? null : bean.initMethod(),
                    // destroy method
                     bean.destoryMethod().isEmpty() ? null : bean.destoryMethod(), 
                     // @PostConstruct / @PreDestroy method:
                     null, null);
                addBeanDefinitions(defs, def);
                logger.atDebug().log("define bean: {}", def);
            }
        }
    }

    /**
     * 将 BeanDefinition 安全加入到 Map 中
     * @param defs 存放 beans 的 Map
     * @param def 需要放入的 BeanDefinition
     */
    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    /**
     * Get public constructor or non-public constructor as fallback.
     * 获取对应类的构造器(优先public)
     * @param clazz 待获取构造器的类
     * @return 构造器
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        // 获取 public 的构造器
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0) {
            // 获取所有访问控制级别的构造器
            cons = clazz.getDeclaredConstructors();
            if (cons.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }
        if (cons.length != 1) {
            throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
        }
        return cons[0];
    }

    /**
     * 获取对应类上 Order注解的值，若没有order注解则返回int类型最大值
     * @param clazz 需要获取order注解的类
     * @return
     */
    int getOrder(Class<?> clazz) {
        Order annotation = clazz.getAnnotation(Order.class);
        return annotation == null ? Integer.MAX_VALUE : annotation.value();
    }

    /**
     * 获取方法上 @Order 注解的值
     * @param method 
     * @return
     */
    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : annotation.value();
    }

    // 根据 name 查找 BeanDefinition，如果不存在返回 null
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
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

    /**
     * 根据 Type 查找某个 BeanDefinition,如果不存在则返回 null.如果存在多个则返回 @Primary 标注的那个
     * @param type
     * @return
     */
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
            List<String> classList = rr.scan(res -> {
                // 遇到以 .class 结尾的文件，就将其转换为 Class 全名:
                String name = res.name();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            if (logger.isDebugEnabled()) {
                classList.forEach((className) -> {
                    logger.debug("class found by component scan: {}", className);
                });
            }
            classNameSet.addAll(classList);

        }

        // 查找 @Import(Xyz.class)
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                if (classNameSet.contains(importClassName)) {
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                } else {
                    logger.debug("class found by importer: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;
    }

    /**
     * 判断是否有@Configuration注解
     * @param def 需要判断的类
     * @return 是否有 @Configuration 注解
     */
    boolean isConfigurationDefinition(BeanDefinition def) {
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
    }
}
