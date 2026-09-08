import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Engine {
    private final ConcurrentHashMap<String, String> stringStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> hashStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LinkedList<String>> listStore = new ConcurrentHashMap<>();

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

    public int lpush(String key, List<String> values) {
        LinkedList<String> list = listStore.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (list) {
            for (String val : values) {
                list.addFirst(val);
            }
            return list.size();
        }
    }

    public int rpush(String key, List<String> values) {
        LinkedList<String> list = listStore.computeIfAbsent(key, k -> new LinkedList<>());
        synchronized (list) {
            for (String val : values) {
                list.addLast(val);
            }
            return list.size();
        }
    }

    public String lpop(String key) {
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
        LinkedList<String> list = listStore.get(key);
        if (list == null) return 0;
        synchronized (list) {
            return list.size();
        }
    }

    public List<String> lrange(String key, int start, int stop) {
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
}