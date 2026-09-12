---
title: GC Roots
tags:
  - Java
  - JVM
  - GC
---

# GC Roots

可达性分析从 GC Roots 出发，能到达的对象存活，不可达的回收。

## GC Roots 包括

1. 虚拟机栈中引用的对象
2. 方法区中类静态属性引用的对象
3. 方法区中常量引用的对象
4. JNI 引用的对象
5. 活跃线程与锁持有的对象

理解 GC Roots 是读懂 [[G1 垃圾回收器]] 与 [[JVM 内存结构]] 的前提。
