package com.stealth.monitor;

import rikka.shizuku.Shizuku;
import java.lang.reflect.Method;

public class ShizukuHelper {
    // Using reflection to bypass the 'private access' compilation bug in the Shizuku API AAR
    public static Process execute(String[] cmd, String[] env, String dir) throws Exception {
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        return (Process) m.invoke(null, cmd, env, dir);
    }
}
