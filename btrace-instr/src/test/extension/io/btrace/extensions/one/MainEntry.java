package io.btrace.extensions.one;

public class MainEntry {
    public static int fld = 0;

    public static MainEntry createInstance() {
        return new MainEntry();
    }

    public static void ext_test(String msg) {
        System.out.println("+++ " + msg + " +++");
    }

    public static String[] aaa(int len) {
        return new String[len];
    }

    public static String a(int x) {
        return  "str" + x;
    }

    public void huhu(String msg) {
        System.out.println("--- " + msg + " ---");
    }

    public static Service newService() {
        return new Service();
    }

    public static void failing() {
        throw new RuntimeException();
    }
}
