import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Engine {
    private final ConcurrentHashMap<String, String> stringStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hashStore = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        stringStore.put(key, value);
    }

    public String get(String key) {
        return stringStore.get(key);
    }

    public long incrBy(String key, long delta) throws NumberFormatException {
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
        String val = stringStore.get(key);
        return val == null ? 0 : val.length();
    }

    public void mset(List<String> keys, List<String> values) {
        for (int i = 0; i < keys.size(); i++) {
            stringStore.put(keys.get(i), values.get(i));
        }
    }

    public List<String> mget(List<String> keys) {
        List<String> results = new ArrayList<>(keys.size());
        for (String key : keys) {
            results.add(stringStore.get(key));
        }
        return results;
    }

    public int hset(String key, List<String> fields, List<String> values) {
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
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null) return null;
        return hash.get(field);
    }

    public List<String> hgetall(String key) {
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
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        if (hash == null) return 0;
        return hash.containsKey(field) ? 1 : 0;
    }

    public int hlen(String key) {
        ConcurrentHashMap<String, String> hash = hashStore.get(key);
        return hash == null ? 0 : hash.size();
    }
}