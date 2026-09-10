import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Engine {
    private final ConcurrentHashMap<String, String> stringStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hashStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<String>> listStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> setStore = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Long> expires = new ConcurrentHashMap<>();
    private final ScheduledExecutorService activeExpiryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "active-expiry-worker");
        t.setDaemon(true);
        return t;
    });

    public Engine() {
        activeExpiryExecutor.scheduleAtFixedRate(this::activeExpireSweep, 100, 100, TimeUnit.MILLISECONDS);
    }

    private boolean checkExpired(String key) {
        Long expTime = expires.get(key);
        if (expTime != null && System.currentTimeMillis() > expTime) {
            deleteKey(key);
            return true;
        }
        return false;
    }

    private void activeExpireSweep() {
        if (expires.isEmpty()) return;

        long now = System.currentTimeMillis();
        int count = 0;
        Iterator<Map.Entry<String, Long>> it = expires.entrySet().iterator();

        while (it.hasNext() && count < 20) {
            Map.Entry<String, Long> entry = it.next();
            if (now > entry.getValue()) {
                deleteKey(entry.getKey());
            }
            count++;
        }
    }

    private void deleteKey(String key) {
        expires.remove(key);
        stringStore.remove(key);
        hashStore.remove(key);
        listStore.remove(key);
        setStore.remove(key);
    }

    public void set(String key, String value) {
        expires.remove(key);
        stringStore.put(key, value);
    }

    public String get(String key) {
        if (checkExpired(key)) return null;
        return stringStore.get(key);
    }

    public long incrBy(String key, long delta) throws NumberFormatException {
        checkExpired(key);
        final long[] resultHolder = new long[1];

        stringStore.compute(key, (k, oldVal) -> {
            long currentVal = 0;
            if (oldVal != null) {
                currentVal = Long.parseLong(oldVal);
            }
            long newVal = currentVal + delta;
            resultHolder[0] = newVal;
            return String.valueOf(newVal);
        });

        return resultHolder[0];
    }

    public int strLen(String key) {
        if (checkExpired(key)) return 0;
        String val = stringStore.get(key);
        return val == null ? 0 : val.length();
    }

    public void mset(List<String> keys, List<String> values) {
        for (int i = 0; i < keys.size(); i++) {
            set(keys.get(i), values.get(i));
        }
    }

    public List<String> mget(List<String> keys) {
        List<String> results = new ArrayList<>(keys.size());
        for (String key : keys) {
            results.add(get(key));
        }
        return results;
    }

    public int hset(String key, List<String> fields, List<String> values) {
        checkExpired(key);
        ConcurrentHashMap<String, String> hash = hashStore.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int addedCount = 0;
        for (int i = 0; i < fields.size(); i++) {
            if (hash.put(fields.get(i), values.get(i)) == null) {
                addedCount++;
            }
        }
        return addedCount;
    }

    public String hget(String key, String field) {
        if (checkExpired(key)) return null;
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null) return null;
        return hash.get(field);
    }

    public List<String> hgetall(String key) {
        if (checkExpired(key)) return Collections.emptyList();
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null || hash.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> flatList = new ArrayList<>(hash.size() * 2);
        for (Map.Entry<String, String> entry : hash.entrySet()) {
            flatList.add(entry.getKey());
            flatList.add(entry.getValue());
        }
        return flatList;
    }

    public int hdel(String key, List<String> fields) {
        if (checkExpired(key)) return 0;
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null) return 0;

        int removedCount = 0;
        for (String field : fields) {
            if (hash.remove(field) != null) {
                removedCount++;
            }
        }

        if (hash.isEmpty()) {
            hashStore.remove(key, hash);
        }

        return removedCount;
    }

    public int hexists(String key, String field) {
        if (checkExpired(key)) return 0;
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null) return 0;
        return hash.containsKey(field) ? 1 : 0;
    }

    public int hlen(String key) {
        if (checkExpired(key)) return 0;
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        return hash == null ? 0 : hash.size();
    }

    public int lpush(String key, List<String> values) {
        checkExpired(key);
        LinkedList<String> list = listStore.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (list) {
            for (String val : values) {
                list.addFirst(val);
            }
            return list.size();
        }
    }

    public int rpush(String key, List<String> values) {
        checkExpired(key);
        LinkedList<String> list = listStore.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (list) {
            for (String val : values) {
                list.addLast(val);
            }
            return list.size();
        }
    }

    public String lpop(String key) {
        if (checkExpired(key)) return null;
        LinkedList<String> list = listStore.get(key);
        if (list == null) return null;
        synchronized (list) {
            if (list.isEmpty()) return null;
            String val = list.removeFirst();
            if (list.isEmpty()) {
                listStore.remove(key, list);
            }
            return val;
        }
    }

    public String rpop(String key) {
        if (checkExpired(key)) return null;
        LinkedList<String> list = listStore.get(key);
        if (list == null) return null;
        synchronized (list) {
            if (list.isEmpty()) return null;
            String val = list.removeLast();
            if (list.isEmpty()) {
                listStore.remove(key, list);
            }
            return val;
        }
    }

    public int llen(String key) {
        if (checkExpired(key)) return 0;
        LinkedList<String> list = listStore.get(key);
        if (list == null) return 0;
        synchronized (list) {
            return list.size();
        }
    }

    public List<String> lrange(String key, int start, int stop) {
        if (checkExpired(key)) return Collections.emptyList();
        LinkedList<String> list = listStore.get(key);
        if (list == null) return Collections.emptyList();

        synchronized (list) {
            int size = list.size();
            if (size == 0) return Collections.emptyList();

            if (start < 0) start = size + start;
            if (stop < 0) stop = size + stop;

            if (start < 0) start = 0;
            if (start >= size || start > stop) {
                return Collections.emptyList();
            }
            if (stop >= size) stop = size - 1;

            List<String> result = new ArrayList<>(stop - start + 1);
            for (int i = start; i <= stop; i++) {
                result.add(list.get(i));
            }
            return result;
        }
    }

    public int sadd(String key, List<String> members) {
        checkExpired(key);
        Set<String> set = setStore.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        int addedCount = 0;
        for (String member : members) {
            if (set.add(member)) {
                addedCount++;
            }
        }
        return addedCount;
    }

    public List<String> smembers(String key) {
        if (checkExpired(key)) return Collections.emptyList();
        Set<String> set = setStore.get(key);
        if (set == null || set.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(set);
    }

    public int sismember(String key, String member) {
        if (checkExpired(key)) return 0;
        Set<String> set = setStore.get(key);
        if (set == null) return 0;
        return set.contains(member) ? 1 : 0;
    }

    public int srem(String key, List<String> members) {
        if (checkExpired(key)) return 0;
        Set<String> set = setStore.get(key);
        if (set == null) return 0;

        int removedCount = 0;
        for (String member : members) {
            if (set.remove(member)) {
                removedCount++;
            }
        }

        if (set.isEmpty()) {
            setStore.remove(key, set);
        }

        return removedCount;
    }

    public int scard(String key) {
        if (checkExpired(key)) return 0;
        Set<String> set = setStore.get(key);
        return set == null ? 0 : set.size();
    }

    public String type(String key) {
        if (checkExpired(key)) return "none";
        if (stringStore.containsKey(key)) return "string";
        if (hashStore.containsKey(key)) return "hash";
        if (listStore.containsKey(key)) return "list";
        if (setStore.containsKey(key)) return "set";
        return "none";
    }

    public int exists(List<String> keys) {
        int count = 0;
        for (String key : keys) {
            if (!checkExpired(key)) {
                if (stringStore.containsKey(key) ||
                    hashStore.containsKey(key) ||
                    listStore.containsKey(key) ||
                    setStore.containsKey(key)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int del(List<String> keys) {
        int removedCount = 0;
        for (String key : keys) {
            boolean removed = false;
            expires.remove(key);
            if (stringStore.remove(key) != null) removed = true;
            if (hashStore.remove(key) != null) removed = true;
            if (listStore.remove(key) != null) removed = true;
            if (setStore.remove(key) != null) removed = true;

            if (removed) {
                removedCount++;
            }
        }
        return removedCount;
    }

    public List<String> keys(String pattern) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(stringStore.keySet());
        allKeys.addAll(hashStore.keySet());
        allKeys.addAll(listStore.keySet());
        allKeys.addAll(setStore.keySet());

        List<String> validKeys = new ArrayList<>();
        for (String k : allKeys) {
            if (!checkExpired(k)) {
                if ("*".equals(pattern) || k.equals(pattern)) {
                    validKeys.add(k);
                }
            }
        }
        return validKeys;
    }

    public int expireAt(String key, long timestampMillis) {
        if (!keyExists(key)) {
            return 0;
        }
        if (timestampMillis <= System.currentTimeMillis()) {
            deleteKey(key);
            return 1;
        }
        expires.put(key, timestampMillis);
        return 1;
    }

    public int pexpire(String key, long milliseconds) {
        return expireAt(key, System.currentTimeMillis() + milliseconds);
    }

    public int expire(String key, long seconds) {
        return pexpire(key, seconds * 1000L);
    }

    public long pttl(String key) {
        if (!keyExists(key)) {
            return -2;
        }
        Long exp = expires.get(key);
        if (exp == null) {
            return -1;
        }
        long diff = exp - System.currentTimeMillis();
        return diff > 0 ? diff : -2;
    }

    public long ttl(String key) {
        long pttlVal = pttl(key);
        if (pttlVal < 0) return pttlVal;
        return (pttlVal + 999) / 1000;
    }

    public int persist(String key) {
        if (!keyExists(key)) {
            return 0;
        }
        return expires.remove(key) != null ? 1 : 0;
    }

    private boolean keyExists(String key) {
        if (checkExpired(key)) return false;
        return stringStore.containsKey(key) ||
               hashStore.containsKey(key) ||
               listStore.containsKey(key) ||
               setStore.containsKey(key);
    }
}