# Mini-Redis in Java 

A high-performance, from-scratch implementation of a Redis-compatible in-memory database built in Java (JDK 21+). Designed step-by-step over a 15-day systems engineering sprint covering custom binary/wire protocols, non-blocking I/O multiplexing, persistence, and atomic transactions.

---

##  Features

- **RESP (REdis Serialization Protocol) Engine**: Full recursive parser and serializer supporting Simple Strings, Errors, Integers, Bulk Strings, Nulls, and Arrays.
- **Data Structures**:
  - **Strings**: `SET`, `GET`, `INCR`, `DECR`, `INCRBY`, `DECRBY`, `STRLEN`, `MSET`, `MGET`
  - **Hashes**: `HSET`, `HGET`, `HGETALL`, `HDEL`, `HEXISTS`, `HLEN`
  - **Lists**: `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN`, `LRANGE`
  - **Sets**: `SADD`, `SMEMBERS`, `SISMEMBER`, `SREM`, `SCARD`
- **Key Expiration & Eviction**:
  - Millisecond and second TTLs (`EXPIRE`, `PEXPIRE`, `TTL`, `PTTL`, `PERSIST`).
  - Dual eviction: **Passive (lazy)** eviction on key access + **Active (background)** randomized sweeper daemon.
- **Universal Key Management**: `DEL`, `EXISTS`, `TYPE`, `KEYS *`.
- **High-Performance Architecture**:
  - Scaled from Virtual Threads to a Single-Threaded Non-Blocking I/O Event Loop using `java.nio.channels.Selector` and non-blocking channels (`epoll`/`kqueue` model).
- **Persistence (AOF)**:
  - Append-Only File (`appendonly.aof`) persistent logging of state-mutating commands in native wire RESP format.
  - Automatic boot recovery engine replaying historical logs into RAM before opening socket accept.
- **Atomic Transactions**: `MULTI`, `EXEC`, `DISCARD` with per-connection command queuing and isolated batch execution.

---

## 📋 15-Day Sprint Roadmap

- [x] **Day 1**: Raw TCP Socket & Virtual Thread Server Loop
- [x] **Day 2**: RESP Protocol Parser (Bulk Strings, Arrays, Raw Frames)
- [x] **Day 3**: RESP Serializer & Foundation Commands (`PING`, `ECHO`)
- [x] **Day 4**: Core Key-Value Engine (`SET`, `GET`)
- [x] **Day 5**: Atomic Integers & Multi-Key Commands (`INCR`, `DECR`, `MSET`, `MGET`, `STRLEN`)
- [x] **Day 6**: Hashes (`HSET`, `HGET`, `HGETALL`, `HDEL`, `HEXISTS`, `HLEN`)
- [x] **Day 7**: Lists (`LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN`, `LRANGE`)
- [x] **Day 8**: Sets (`SADD`, `SMEMBERS`, `SISMEMBER`, `SREM`, `SCARD`)
- [x] **Day 9**: Universal Key Management (`DEL`, `EXISTS`, `TYPE`, `KEYS`)
- [x] **Day 10**: Dual Expiration Engine (Passive Eviction + Active Sweeper Daemon)
- [x] **Day 11**: Non-Blocking I/O Event Loop (`java.nio.channels.Selector`)
- [x] **Day 12**: Append-Only File (AOF) Persistence Writer
- [x] **Day 13**: AOF Recovery Engine (Replay persistence log on startup)
- [x] **Day 14**: Atomic Transactions (`MULTI`, `EXEC`, `DISCARD`)
- [x] **Day 15**: Benchmark Hardening (`redis-benchmark`) & Buffer Resilience

---

##  Build & Run

### Prerequisites
- JDK 21+
- `redis-cli` and `redis-benchmark`

### 1. Compile
```bash
javac Aof.java RespParser.java Engine.java RedisServer.java
