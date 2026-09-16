---
title: MySQL 索引与事务
tags:
  - MySQL
  - Database
---

# MySQL

MySQL 是最常用的开源关系型数据库。

## 索引

- B+ 树索引：等值查询与范围查询，叶子节点存主键（聚簇索引）
- 慢查询优化：先看 `EXPLAIN` 执行计划，避免全表扫描与最左前缀失效
- 覆盖索引：查询列都在索引里，免回表

## 事务

ACID 四特性：原子性、一致性、隔离性、持久性。

| 隔离级别 | 脏读 | 不可重复读 | 幻读 |
| --- | --- | --- | --- |
| READ UNCOMMITTED | 有 | 有 | 有 |
| READ COMMITTED | 无 | 有 | 有 |
| REPEATABLE READ | 无 | 无 | InnoDB 基本避免 |
| SERIALIZABLE | 无 | 无 | 无 |

InnoDB 默认 REPEATABLE READ，通过 MVCC（多版本并发控制）+ 间隙锁实现。
