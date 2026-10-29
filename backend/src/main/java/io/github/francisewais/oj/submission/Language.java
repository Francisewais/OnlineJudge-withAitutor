package io.github.francisewais.oj.submission;

import java.util.Arrays;
import java.util.Optional;

/** 支持的语言，需要和判题机 judger/app/languages.py 保持一致。 */
public enum Language {
    C("c", "C (GCC 13, C17)", "c", 1),
    CPP("cpp", "C++ (G++ 13, C++17)", "cpp", 1),
    JAVA("java", "Java 21", "java", 2),
    PYTHON("python", "Python 3.12", "python", 3);

    private final String id;
    private final String displayName;
    /** Markdown 代码块的语言标记 */
    private final String fence;
    /** 时间限制的倍数 */
    private final int timeFactor;

    Language(String id, String displayName, String fence, int timeFactor) {
        this.id = id;
        this.displayName = displayName;
        this.fence = fence;
        this.timeFactor = timeFactor;
    }

    public static Optional<Language> of(String id) {
        return Arrays.stream(values()).filter(l -> l.id.equals(id)).findFirst();
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String fence() {
        return fence;
    }

    public int timeFactor() {
        return timeFactor;
    }
}
