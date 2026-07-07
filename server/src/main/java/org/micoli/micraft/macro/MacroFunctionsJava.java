package org.micoli.micraft.macro;

public class MacroFunctionsJava {
    public interface Callback {
        void call(String arg);
    }

    private static final ThreadLocal<Callback> threadSend = new ThreadLocal<>();
    private static final ThreadLocal<Callback> threadAction = new ThreadLocal<>();

    public static void setSendCallback(Callback cb) {
        threadSend.set(cb);
    }

    public static void setActionCallback(Callback cb) {
        threadAction.set(cb);
    }

    public static void clearCallbacks() {
        threadSend.remove();
        threadAction.remove();
    }

    public static void send(String cmd) {
        Callback cb = threadSend.get();
        if (cb != null) cb.call(cmd);
    }

    public static void action(String act) {
        Callback cb = threadAction.get();
        if (cb != null) cb.call(act);
    }

}
