---
title: HashMap
tags:
  - Java
  - Collection
  - 面试
status: learning
created: 2026-09-05
---

# HashMap

HashMap 是 Java 中最常用的 **键值对（Key-Value）容器**，基于哈希表实现，允许 null 键和 null 值，线程不安全。

## 底层结构

```
数组 + 链表 + 红黑树（JDK 8+）
```

- 数组：主结构，容量恒为 2 的幂
- 链表：哈希冲突时挂链，长度超过 8 且数组容量 ≥ 64 时树化
- 红黑树：树化后查询从 O(n) 降到 O(log n)

## 核心参数

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| initialCapacity | 16 | 初始容量，扩容为 2 倍 |
| loadFactor | 0.75 | 负载因子，size > capacity × 0.75 时扩容 |
| TREEIFY_THRESHOLD | 8 | 链表树化阈值 |

## 为什么容量是 2 的幂

`index = hash & (capacity - 1)`，位运算取模效率高，且能让散列更均匀。

## 并发问题

HashMap 线程不安全：并发扩容可能导致数据丢失，JDK 7 头插法还可能成环。并发场景请使用 ConcurrentHashMap，CAS 相关原理见 [[CAS]]，线程协作基础见 [[并发编程]]。

#Java #学习笔记
