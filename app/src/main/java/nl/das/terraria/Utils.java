package nl.das.terraria;

import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static String cvthm2string(int hour, int minute) {
        if (hour == 0 && minute == 0) {
            return "";
        } else {
            return String.format("%02d.%02d", hour, minute);
        }
    }
    public static int getH(String tm) {
        if (tm.trim().length() == 0) {
            return 0;
        } else {
            return Integer.parseInt(tm.split("\\.")[0]);
        }
    }
    public static int getM(String tm) {
        if (tm.trim().length() == 0) {
            return 0;
        } else {
            return Integer.parseInt(tm.split("\\.")[1]);
        }
    }
}
