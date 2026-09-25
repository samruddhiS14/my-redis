import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class RedisServer {
    private static final int PORT = 6379;
    private static final Engine engine = new Engine();
    private static Aof aof;

    private static class ClientSession {
        ByteBuffer buffer = ByteBuffer.allocate(16384);
        boolean inTransaction = false;
        List<List<String>> queue = new ArrayList<>();

        void ensureCapacity(int needed) {
            if (buffer.remaining() < needed) {
                ByteBuffer bigger = ByteBuffer.allocate(buffer.capacity() * 2);
                buffer.flip();
                bigger.put(buffer);
                buffer = bigger;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Initializing Redis Server (Hardened NIO) on port " + PORT + "...");

        loadAof();

        try {
            aof = new Aof();
        } catch (IOException e) {
            System.err.println("Failed to initialize AOF: " + e.getMessage());
            return;
        }

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open();
             Selector selector = Selector.open()) {

            serverChannel.bind(new InetSocketAddress(PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server ready for high-load connections on port " + PORT);

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        acceptClient(serverChannel, selector);
                    } else if (key.isReadable()) {
                        readClient(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
        }
    }

    private static void loadAof() {
        try {
            List<List<String>> commands = Aof.readCommands();
            if (!commands.isEmpty()) {
                System.out.println("Loading AOF file: replaying " + commands.size() + " commands...");
                for (List<String> cmd : commands) {
                    dispatchCommand(cmd);
                }
                System.out.println("AOF loaded successfully. State restored.");
            }
        } catch (IOException e) {
            System.err.println("Error reading AOF during boot: " + e.getMessage());
        }
    }

    private static void acceptClient(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.socket().setTcpNoDelay(true);
            ClientSession session = new ClientSession();
            clientChannel.register(selector, SelectionKey.OP_READ, session);
        }
    }

    private static void readClient(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        try {
            session.ensureCapacity(1024);
            int bytesRead = clientChannel.read(session.buffer);
            if (bytesRead == -1) {
                closeConnection(key, clientChannel);
                return;
            }

            session.buffer.flip();

            while (true) {
                List<String> commandArgs = RespParser.parseBufferCommand(session.buffer);
                if (commandArgs == null) {
                    break;
                }

                byte[] response = handleCommandWithTransaction(session, commandArgs);
                writeFully(clientChannel, response);
            }

            session.buffer.compact();
        } catch (IOException e) {
            closeConnection(key, clientChannel);
        }
    }

    private static void writeFully(SocketChannel channel, byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private static byte[] handleCommandWithTransaction(ClientSession session, List<String> commandArgs) {
        String cmd = commandArgs.get(0).toUpperCase();

        if ("MULTI".equals(cmd)) {
            if (session.inTransaction) return RespParser.toError("MULTI calls can not be nested");
            session.inTransaction = true;
            session.queue.clear();
            return RespParser.toSimpleString("OK");
        }

        if ("DISCARD".equals(cmd)) {
            if (!session.inTransaction) return RespParser.toError("DISCARD without MULTI");
            session.inTransaction = false;
            session.queue.clear();
            return RespParser.toSimpleString("OK");
        }

        if ("EXEC".equals(cmd)) {
            if (!session.inTransaction) return RespParser.toError("EXEC without MULTI");
            session.inTransaction = false;
            List<byte[]> results = new ArrayList<>(session.queue.size());

            for (List<String> queuedArgs : session.queue) {
                byte[] res = dispatchCommand(queuedArgs);
                aof.writeCommand(queuedArgs);
                results.add(res);
            }

            session.queue.clear();
            return RespParser.toArray(results);
        }

        if (session.inTransaction) {
            session.queue.add(commandArgs);
            return RespParser.toSimpleString("QUEUED");
        }

        byte[] response = dispatchCommand(commandArgs);
        aof.writeCommand(commandArgs);
        return response;
    }

    private static void closeConnection(SelectionKey key, SocketChannel channel) {
        try {
            key.cancel();
            channel.close();
        } catch (IOException ignored) {
        }
    }

    private static byte[] dispatchCommand(List<String> args) {
        String command = args.get(0).toUpperCase();

        switch (command) {
            case "PING":
                if (args.size() == 1) return RespParser.toSimpleString("PONG");
                return RespParser.toBulkString(args.get(1));

            case "ECHO":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'echo' command");
                return RespParser.toBulkString(args.get(1));

            case "SET":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'set' command");
                engine.set(args.get(1), args.get(2));
                return RespParser.toSimpleString("OK");

            case "GET":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'get' command");
                return RespParser.toBulkString(engine.get(args.get(1)));

            case "INCR":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'incr' command");
                try {
                    long newVal = engine.incrBy(args.get(1), 1);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "DECR":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'decr' command");
                try {
                    long newVal = engine.incrBy(args.get(1), -1);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "INCRBY":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'incrby' command");
                try {
                    long delta = Long.parseLong(args.get(2));
                    long newVal = engine.incrBy(args.get(1), delta);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "DECRBY":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'decrby' command");
                try {
                    long delta = Long.parseLong(args.get(2));
                    long newVal = engine.incrBy(args.get(1), -delta);
                    return RespParser.toInteger(newVal);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "STRLEN":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'strlen' command");
                return RespParser.toInteger(engine.strLen(args.get(1)));

            case "MSET":
                if (args.size() < 3 || (args.size() - 1) % 2 != 0) return RespParser.toError("wrong number of arguments for 'mset' command");
                List<String> msetKeys = new ArrayList<>();
                List<String> msetVals = new ArrayList<>();
                for (int i = 1; i < args.size(); i += 2) {
                    msetKeys.add(args.get(i));
                    msetVals.add(args.get(i + 1));
                }
                engine.mset(msetKeys, msetVals);
                return RespParser.toSimpleString("OK");

            case "MGET":
                if (args.size() < 2) return RespParser.toError("wrong number of arguments for 'mget' command");
                List<String> queryKeys = args.subList(1, args.size());
                List<String> results = engine.mget(queryKeys);
                List<byte[]> serializedValues = new ArrayList<>(results.size());
                for (String val : results) {
                    serializedValues.add(RespParser.toBulkString(val));
                }
                return RespParser.toArray(serializedValues);

            case "HSET":
                if (args.size() < 4 || (args.size() - 2) % 2 != 0) return RespParser.toError("wrong number of arguments for 'hset' command");
                String hsetKey = args.get(1);
                List<String> fields = new ArrayList<>();
                List<String> vals = new ArrayList<>();
                for (int i = 2; i < args.size(); i += 2) {
                    fields.add(args.get(i));
                    vals.add(args.get(i + 1));
                }
                int added = engine.hset(hsetKey, fields, vals);
                return RespParser.toInteger(added);

            case "HGET":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'hget' command");
                return RespParser.toBulkString(engine.hget(args.get(1), args.get(2)));

            case "HGETALL":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'hgetall' command");
                List<String> allEntries = engine.hgetall(args.get(1));
                List<byte[]> serializedHash = new ArrayList<>(allEntries.size());
                for (String item : allEntries) {
                    serializedHash.add(RespParser.toBulkString(item));
                }
                return RespParser.toArray(serializedHash);

            case "HDEL":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'hdel' command");
                return RespParser.toInteger(engine.hdel(args.get(1), args.subList(2, args.size())));

            case "HEXISTS":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'hexists' command");
                return RespParser.toInteger(engine.hexists(args.get(1), args.get(2)));

            case "HLEN":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'hlen' command");
                return RespParser.toInteger(engine.hlen(args.get(1)));

            case "LPUSH":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'lpush' command");
                return RespParser.toInteger(engine.lpush(args.get(1), args.subList(2, args.size())));

            case "RPUSH":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'rpush' command");
                return RespParser.toInteger(engine.rpush(args.get(1), args.subList(2, args.size())));

            case "LPOP":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'lpop' command");
                return RespParser.toBulkString(engine.lpop(args.get(1)));

            case "RPOP":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'rpop' command");
                return RespParser.toBulkString(engine.rpop(args.get(1)));

            case "LLEN":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'llen' command");
                return RespParser.toInteger(engine.llen(args.get(1)));

            case "LRANGE":
                if (args.size() != 4) return RespParser.toError("wrong number of arguments for 'lrange' command");
                try {
                    int start = Integer.parseInt(args.get(2));
                    int stop = Integer.parseInt(args.get(3));
                    List<String> items = engine.lrange(args.get(1), start, stop);
                    List<byte[]> serializedList = new ArrayList<>(items.size());
                    for (String item : items) {
                        serializedList.add(RespParser.toBulkString(item));
                    }
                    return RespParser.toArray(serializedList);
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "SADD":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'sadd' command");
                return RespParser.toInteger(engine.sadd(args.get(1), args.subList(2, args.size())));

            case "SMEMBERS":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'smembers' command");
                List<String> members = engine.smembers(args.get(1));
                List<byte[]> serializedMembers = new ArrayList<>(members.size());
                for (String member : members) {
                    serializedMembers.add(RespParser.toBulkString(member));
                }
                return RespParser.toArray(serializedMembers);

            case "SISMEMBER":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'sismember' command");
                return RespParser.toInteger(engine.sismember(args.get(1), args.get(2)));

            case "SREM":
                if (args.size() < 3) return RespParser.toError("wrong number of arguments for 'srem' command");
                return RespParser.toInteger(engine.srem(args.get(1), args.subList(2, args.size())));

            case "SCARD":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'scard' command");
                return RespParser.toInteger(engine.scard(args.get(1)));

            case "TYPE":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'type' command");
                return RespParser.toSimpleString(engine.type(args.get(1)));

            case "EXISTS":
                if (args.size() < 2) return RespParser.toError("wrong number of arguments for 'exists' command");
                return RespParser.toInteger(engine.exists(args.subList(1, args.size())));

            case "DEL":
                if (args.size() < 2) return RespParser.toError("wrong number of arguments for 'del' command");
                return RespParser.toInteger(engine.del(args.subList(1, args.size())));

            case "KEYS":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'keys' command");
                List<String> matchedKeys = engine.keys(args.get(1));
                List<byte[]> serializedKeys = new ArrayList<>(matchedKeys.size());
                for (String k : matchedKeys) {
                    serializedKeys.add(RespParser.toBulkString(k));
                }
                return RespParser.toArray(serializedKeys);

            case "EXPIRE":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'expire' command");
                try {
                    long sec = Long.parseLong(args.get(2));
                    return RespParser.toInteger(engine.expire(args.get(1), sec));
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "PEXPIRE":
                if (args.size() != 3) return RespParser.toError("wrong number of arguments for 'pexpire' command");
                try {
                    long ms = Long.parseLong(args.get(2));
                    return RespParser.toInteger(engine.pexpire(args.get(1), ms));
                } catch (NumberFormatException e) {
                    return RespParser.toError("value is not an integer or out of range");
                }

            case "TTL":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'ttl' command");
                return RespParser.toInteger(engine.ttl(args.get(1)));

            case "PTTL":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'pttl' command");
                return RespParser.toInteger(engine.pttl(args.get(1)));

            case "PERSIST":
                if (args.size() != 2) return RespParser.toError("wrong number of arguments for 'persist' command");
                return RespParser.toInteger(engine.persist(args.get(1)));

            default:
                return RespParser.toError("unknown command '" + command + "'");
        }
    }
}