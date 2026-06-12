package com.stealth.monitor;

import rikka.shizuku.Shizuku;
import java.io.IOException;

public class ShizukuHelper {
    public static Process execute(String[] cmd, String[] env, String dir) throws IOException {
        return Shizuku.newProcess(cmd, env, dir);
    }
}
