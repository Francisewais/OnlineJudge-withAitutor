package io.github.francisewais.oj.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import io.github.francisewais.oj.ai.TutorPrompt.JudgeInfo;
import io.github.francisewais.oj.ai.TutorPrompt.Message;
import io.github.francisewais.oj.ai.TutorPrompt.ProblemInfo;
import io.github.francisewais.oj.problem.ProblemDtos.Sample;
import io.github.francisewais.oj.submission.Language;
import io.github.francisewais.oj.submission.SubmissionDtos.CaseResult;
import org.junit.jupiter.api.Test;

class TutorPromptTest {

    private final ProblemInfo problem = new ProblemInfo("A + B", "EASY", 1000, 256, "计算 a + b 的值。",
            "一行两个整数 a, b。", "输出一个整数。", List.of(new Sample("1 2", "3")), "", "直接相加即可。");

    @Test
    void containsProblemCodeAndFirstFailedCase() {
        var judge = new JudgeInfo("FINISHED", "RE", 1, 3, null, List.of(
                new CaseResult("AC", 1, 1024, 0, 0, ""),
                new CaseResult("RE", 2, 1024, 0, 11, "段错误（Segmentation fault）"),
                new CaseResult("WA", 1, 1024, 0, 0, "")), false);

        List<Message> messages = TutorPrompt.build(problem, TutorMode.DEBUG, Language.CPP, "int main(){}", judge,
                List.of(), null);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo("system");
        assertThat(messages.get(0).content()).contains("# 题目：A + B", "计算 a + b 的值。", "1 2", "直接相加即可。");
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(1).content())
                .contains("【帮我找错】", "```cpp\nint main(){}\n```", "运行出错（RE）", "通过 1/3",
                        "第 2 个测试点", "段错误", TutorMode.DEBUG.defaultQuestion())
                .doesNotContain("第 3 个测试点", "代码可能已经改过");
    }

    @Test
    void compileErrorIncludesMessageAndNotesChangedCode() {
        var judge = new JudgeInfo("FINISHED", "CE", 0, 3, "main.cpp:1:1: error: expected ';'", List.of(), true);

        String user = TutorPrompt.build(problem, TutorMode.HINT, Language.CPP, "int main(){", judge, null, null)
                .get(1).content();

        assertThat(user).contains("编译错误（CE）", "expected ';'", "代码可能已经改过").doesNotContain("测试点");
    }

    @Test
    void chatUsesQuestionAndKeepsRecentHistory() {
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            history.add(new Message(i % 2 == 0 ? "user" : "assistant", "第 " + i + " 条"));
        }

        List<Message> messages = TutorPrompt.build(problem, TutorMode.CHAT, Language.PYTHON, "", null, history,
                "为什么要用 long long？");

        assertThat(messages).hasSize(1 + TutorPrompt.MAX_HISTORY + 1);
        assertThat(messages.get(1).content()).isEqualTo("第 4 条");
        assertThat(messages.getLast().content()).contains("【追问】", "我还没有写代码", "为什么要用 long long？")
                .doesNotContain("最近一次提交");
    }

    @Test
    void longCodeIsTruncated() {
        String code = "x".repeat(TutorPrompt.MAX_CODE_CHARS + 100);

        String user = TutorPrompt.build(problem, TutorMode.DEBUG, Language.C, code, null, null, null).get(1).content();

        assertThat(user).contains("已截断").hasSizeLessThan(TutorPrompt.MAX_CODE_CHARS + 500);
    }
}
