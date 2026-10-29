package io.github.francisewais.oj.ai;

/** AI 助教的几种模式，决定回答透露多少。 */
public enum TutorMode {
    HINT("提示思路", "我没有思路，请给我一点提示。"),
    DEBUG("帮我找错", "我的代码哪里有问题？应该怎么改？"),
    EXPLAIN("讲解题解", "请讲解这道题的解法。"),
    CHAT("追问", "请继续讲。");

    private final String label;
    private final String defaultQuestion;

    TutorMode(String label, String defaultQuestion) {
        this.label = label;
        this.defaultQuestion = defaultQuestion;
    }

    public String label() {
        return label;
    }

    public String defaultQuestion() {
        return defaultQuestion;
    }
}
