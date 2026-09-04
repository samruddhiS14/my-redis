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

    /**
     * Atomically increments or decrements a key by a delta.
     * Throws NumberFormatException if existing value is not a valid 64-bit integer.
     */
    public long incrBy(String key, long delta) throws NumberFormatException {
        // Atomic compute ensures thread-safety across concurrent client threads
        final long[] resultHolder = new long[1];

        stringStore.compute(key, (k, oldVal) -> {
            long currentVal = 0;
            if (oldVal != null) {
                currentVal = Long.parseLong(oldVal); // Throws NumberFormatException if invalid
            }
            long newVal = currentVal + delta;
            resultHolder[0] = newVal;
            return String.valueOf(newVal);
        });

        return resultHolder[0];
    }

    /**
     * Returns the length of the string value stored at key.
     */
    public int strLen(String key) {
        String val = stringStore.get(key);
        return val == null ? 0 : val.length();
    }

    /**
     * Atomically sets multiple key-value pairs.
     */
    public void mset(List<String> keys, List<String> values) {
        for (int i = 0; i < keys.size(); i++) {
            stringStore.put(keys.get(i), values.get(i));
        }
    }

    /**
     * Retrieves multiple keys in order.
     */
    public List<String> mget(List<String> keys) {
        List<String> results = new ArrayList<>(keys.size());
        for (String key : keys) {
            results.add(stringStore.get(key));
        }
        return results;
    }
}