package io.github.francisewais.oj.submission;

import java.util.Map;

public final class Verdicts {

    public static final String AC = "AC";
    public static final String CE = "CE";
    public static final String SE = "SE";

    private static final Map<String, String> NAMES = Map.of(
            "AC", "答案正确",
            "WA", "答案错误",
            "TLE", "运行超时",
            "MLE", "内存超限",
            "RE", "运行出错",
            "OLE", "输出超限",
            "CE", "编译错误",
            "SE", "系统错误");

    private Verdicts() {
    }

    public static String name(String verdict) {
        return NAMES.getOrDefault(verdict, verdict);
    }
}
