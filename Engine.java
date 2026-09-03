import java.util.concurrent.ConcurrentHashMap;

public class Engine {
    private final ConcurrentHashMap<String, String > stringStore = new ConcurrentHashMap< >();
    public void set(String key , String value){
        stringStore.put(key , value);
    }
    public String get(String key){
        return stringStore.get(key);
    }
}
