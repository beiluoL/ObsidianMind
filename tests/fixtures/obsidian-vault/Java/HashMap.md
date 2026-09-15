---
title: Java HashMap
tags:
  - Java
  - Collection
---

# HashMap

HashMap 基于哈希表实现，是非线程安全的键值对容器。

## 存储结构

JDK 8 之后采用「数组 + 链表 + 红黑树」结构，链表长度超过 8 且数组长度达到 64 时转为红黑树。

```java
Map<String, Integer> map = new HashMap<>();
map.put("key", 1);
```

## 扩容机制

默认容量 16，负载因子 0.75；元素数量超过阈值时容量翻倍（扩容为原来的 2 倍）。

并发场景请使用 [[ConcurrentHashMap]]。
