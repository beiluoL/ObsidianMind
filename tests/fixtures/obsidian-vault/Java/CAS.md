---
title: CAS
tags:
  - Java
  - 并发
---

# CAS（Compare And Swap）

CAS 是无锁并发的基础原语：比较内存值与期望值，一致则更新，否则失败重试。

## 三大问题

- **ABA**：版本号解决（AtomicStampedReference）
- **自旋开销**：长期失败浪费 CPU
- **只能保证单变量原子性**：多变量用锁或 AtomicReference

底层依赖 CPU 的 `cmpxchg` 指令，是 [[并发编程]] 中乐观锁的实现基础。
