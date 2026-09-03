package org.micoli.micraft.macro;

public class MacroFunctionsJava {
    public interface Callback {
        void call(String arg);
    }

    /** Server-side bridge for action-block scripts: read/write a named block's variables and
     * remotely trigger its onRemoteEvent. */
    public interface BlockBridge {
        Object get(String block, String var);

        void set(String block, String var, Object value);

        void remote(String block);
    }

    private static final ThreadLocal<Callback> threadSend = new ThreadLocal<>();
    private static final ThreadLocal<Callback> threadAction = new ThreadLocal<>();
    private static final ThreadLocal<Callback> threadNotify = new ThreadLocal<>();
    private static final ThreadLocal<BlockBridge> threadBlocks = new ThreadLocal<>();

    public static void setSendCallback(Callback cb) {
        threadSend.set(cb);
    }

    public static void setActionCallback(Callback cb) {
        threadAction.set(cb);
    }

    public static void setNotifyCallback(Callback cb) {
        threadNotify.set(cb);
    }

    public static void setBlockBridge(BlockBridge b) {
        threadBlocks.set(b);
    }

    public static void clearCallbacks() {
        threadSend.remove();
        threadAction.remove();
        threadNotify.remove();
        threadBlocks.remove();
    }

    /** Opaque snapshot of the current thread's callbacks — for nested execution (remote()). */
    public static final class Snapshot {
        final Callback send;
        final Callback action;
        final Callback notify;
        final BlockBridge blocks;

        Snapshot(Callback send, Callback action, Callback notify, BlockBridge blocks) {
            this.send = send;
            this.action = action;
            this.notify = notify;
            this.blocks = blocks;
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                threadSend.get(), threadAction.get(), threadNotify.get(), threadBlocks.get());
    }

    public static void restore(Snapshot s) {
        if (s.send == null) threadSend.remove();
        else threadSend.set(s.send);
        if (s.action == null) threadAction.remove();
        else threadAction.set(s.action);
        if (s.notify == null) threadNotify.remove();
        else threadNotify.set(s.notify);
        if (s.blocks == null) threadBlocks.remove();
        else threadBlocks.set(s.blocks);
    }

    public static void send(String cmd) {
        Callback cb = threadSend.get();
        if (cb != null) cb.call(cmd);
    }

    public static void action(String act) {
        Callback cb = threadAction.get();
        if (cb != null) cb.call(act);
    }

    /** Send a plain notification to the triggering player (no command parsing). */
    public static void notify(String message) {
        Callback cb = threadNotify.get();
        if (cb != null) cb.call(message);
    }

    public static BlockHandle getBlock(String name) {
        return new BlockHandle(name);
    }

    public static final class BlockHandle {
        private final String name;

        BlockHandle(String name) {
            this.name = name;
        }

        public Object get(String var) {
            BlockBridge b = threadBlocks.get();
            return b == null ? null : b.get(name, var);
        }

        public void set(String var, Object value) {
            BlockBridge b = threadBlocks.get();
            if (b != null) b.set(name, var, value);
        }

        public void remote() {
            BlockBridge b = threadBlocks.get();
            if (b != null) b.remote(name);
        }
    }
}
