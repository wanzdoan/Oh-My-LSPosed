package dev.alastorkaneki.chromescript;

import android.util.Log;

final class ModuleLog {
    interface Sink {
        void write(int priority, String message, Throwable error);
    }

    private static volatile Sink sink;

    private ModuleLog() { }

    static void install(Sink value) {
        sink = value;
    }

    static void i(String message) {
        write(Log.INFO, message, null);
    }

    static void w(String message, Throwable error) {
        write(Log.WARN, message, error);
    }

    private static void write(int priority, String message, Throwable error) {
        Sink current = sink;
        if (current != null) {
            current.write(priority, message, error);
        } else if (error == null) {
            Log.println(priority, "ChromeScript", message);
        } else {
            Log.println(priority, "ChromeScript", message + "\n" + Log.getStackTraceString(error));
        }
    }
}

