# Mini-Redis in Java

A lightweight, from-scratch implementation of a Redis server in Java (Java 21+). Built step-by-step over a 15-day challenge focusing on network protocols, in-memory data structures, non-blocking I/O, and persistence.

---

## 15-Day Implementation Roadmap

- [x] **Day 1**: Raw TCP Socket & Server Loop (`ServerSocket`, Virtual Threads, raw wire test)
- [x] **Day 2**: RESP Protocol Parser (Array & Bulk String decoding)
- [x] **Day 3**: RESP Serializer & Basic Commands (`PING`, `ECHO`)
- [ ] **Day 4**: Core In-Memory Key-Value Store (`GET`, `SET`)
- [ ] **Day 5**: Numeric Operations & Multi-Key (`INCR`, `DECR`, `MGET`, `MSET`)
- [ ] **Day 6**: Hashes (`HSET`, `HGET`, `HGETALL`, `HDEL`)
- [ ] **Day 7**: Lists (`LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`)
- [ ] **Day 8**: Sets (`SADD`, `SMEMBERS`, `SISMEMBER`)
- [ ] **Day 9**: Universal Key Management (`DEL`, `EXISTS`, `TYPE`, `KEYS`)
- [ ] **Day 10**: Expiration Engine (Passive & Active background TTL cleanup)
- [ ] **Day 11**: Non-Blocking I/O Event Loop (`java.nio` Selector)
- [ ] **Day 12**: Append-Only File (AOF) Persistence Writer
- [ ] **Day 13**: AOF Recovery Engine (Replaying logs on startup)
- [ ] **Day 14**: Atomic Transactions (`MULTI`, `EXEC`, `DISCARD`)
- [ ] **Day 15**: Stress Testing & `redis-benchmark` Hardening

---

## How to Run

### Prerequisites
- JDK 21 or higher
- `redis-cli` (or `nc` / `telnet`)

### 1. Compile & Start the Server
```bash
javac RespParser.java RedisServer.java
java RedisServer
