import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Engine {
    private final ConcurrentHashMap<String, String> stringStore = new ConcurrentHashMap<>();

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
}