package org.example.agent.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * 【关键修复】
 * 解决 Spring Boot 3.2+ / Spring 6.1+ 与 Mybatis/Feign 混合使用时的启动冲突。
 * (com.baomidou:mybatis-plus-boot-starter:3.5.7 配合 Spring Boot 3.2.4)
 *
 * 原因是 Spring 6.1 不再允许 'factoryBeanObjectType' 属性为 String。
 *
 * 此 PostProcessor 在 Spring 检查 Bean 定义之前，以最高优先级运行，
 * 强行移除所有 Bean 定义中类型为 String 的 'factoryBeanObjectType' 属性，
 * 从而避免 Spring 启动时抛出 IllegalArgumentException。
 */
@Configuration
public class SpringStartupFix implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 遍历所有已注册的 Bean 定义
        for (String beanName : registry.getBeanDefinitionNames()) {
            org.springframework.beans.factory.config.BeanDefinition beanDefinition =
                    registry.getBeanDefinition(beanName);

            // 检查那个导致崩溃的属性
            Object factoryBeanObjectType = beanDefinition.getAttribute("factoryBeanObjectType");

            // 如果它是 String（问题所在），则将其移除
            if (factoryBeanObjectType instanceof String) {
                beanDefinition.removeAttribute("factoryBeanObjectType");
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 无需操作
    }

    @Override
    public int getOrder() {
        // 必须是最高优先级，确保在 Spring 内置的 PostProcessor 之前运行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}