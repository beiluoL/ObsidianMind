---
title: Spring IOC 与 AOP
tags:
  - Java
  - Spring
---

# Spring IOC 与 AOP

## IOC（控制反转）

对象创建与依赖装配交给容器管理：

- BeanDefinition → BeanFactory → getBean
- 三级缓存解决循环依赖
- 生命周期：实例化 → 属性填充 → 初始化 → 销毁

## AOP（面向切面）

动态代理织入横切逻辑：

- JDK 动态代理（接口）
- CGLIB（ subclass ）

Spring 是 Java 后端工程化的基石，常与 [[JVM 内存结构]] 调优一起出现在面试中。
