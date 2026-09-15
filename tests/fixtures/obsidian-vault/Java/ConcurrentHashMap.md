---
title: ConcurrentHashMap
tags:
  - Java
  - 并发
---

# ConcurrentHashMap

线程安全的哈希表，JDK 8 起采用 CAS + synchronized 锁定单个桶头节点。

## 与 HashMap 的区别

- 线程安全：写操作按桶粒度加锁
- 不允许 null 键和 null 值
- size 通过 CounterCell 分片统计

底层仍是哈希表思想，参见 [[HashMap]] 的存储结构。
