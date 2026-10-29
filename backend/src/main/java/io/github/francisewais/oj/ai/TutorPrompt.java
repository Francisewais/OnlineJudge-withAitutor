package io.github.francisewais.oj.ai;

import java.util.ArrayList;
import java.util.List;

import io.github.francisewais.oj.problem.ProblemDtos.Sample;
import io.github.francisewais.oj.submission.Language;
import io.github.francisewais.oj.submission.SubmissionDtos.CaseResult;
import io.github.francisewais.oj.submission.Verdicts;

/**
 * 拼装发给大模型的消息：系统提示（规则 + 题面），最近几轮对话，本次提问（代码 + 最近一次评测结果）。
 * 隐藏的测试数据不会出现在这里。
 */
public final class TutorPrompt {

    static final int MAX_HISTORY = 6;
    static final int MAX_HISTORY_CHARS = 4000;
    static final int MAX_CODE_CHARS = 20000;

    public record ProblemInfo(String title, String difficulty, int timeLimitMs, int memoryLimitMb,
                              String description, String inputFormat, String outputFormat, List<Sample> samples,
                              String hint, String editorial) {
    }

    /** 最近一次提交的评测结果；codeChanged 表示提交之后代码又改过 */
    public record JudgeInfo(String status, String verdict, int passed, int total, String compileMessage,
                            List<CaseResult> cases, boolean codeChanged) {
    }

    public record Message(String role, String content) {
    }

    private static final String RULES = """
            你是在线评测平台上的编程助教。学生正在做下面这道题，你的目标是帮他自己想明白、自己写出来，而不是替他完成。

            回答要求：
            - 用中文回答，语气友好、简洁，善用 Markdown；代码放在带语言标记的代码块里，数学公式用 $...$。
            - 你看不到隐藏的测试数据，不要编造测试数据的具体内容；可以根据题意推测容易出错的边界情况。
            - 只讨论这道题和编程学习相关的内容，其他请求礼貌地拒绝。

            学生每次提问前会标明模式，按模式控制透露的程度：
            - 【提示思路】只给关键观察、可以用的算法或数据结构、需要达到的时间复杂度，可以提一两个引导性问题；不要给出代码。
            - 【帮我找错】结合学生的代码和评测结果，指出最可能出错的地方和原因（例如边界条件、整数溢出、复杂度太高、输入输出格式不对），说明怎么改；只展示需要修改的几行，不要重写整份代码。
            - 【讲解题解】完整讲解思路、正确性和复杂度，最后给出一份带注释的参考代码，语言和学生使用的语言一致。
            - 【追问】回答学生的追问，保持之前的透露程度；学生没有明确要求时不要给出完整代码。

            """;

    private TutorPrompt() {
    }

    public static List<Message> build(ProblemInfo problem, TutorMode mode, Language language, String code,
                                      JudgeInfo judge, List<Message> history, String question) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt(problem)));
        if (history != null) {
            for (Message m : history.subList(Math.max(0, history.size() - MAX_HISTORY), history.size())) {
                messages.add(new Message(m.role(), truncate(m.content(), MAX_HISTORY_CHARS)));
            }
        }
        messages.add(new Message("user", userMessage(problem, mode, language, code, judge, question)));
        return messages;
    }

    static String systemPrompt(ProblemInfo p) {
        StringBuilder sb = new StringBuilder(RULES);
        sb.append("# 题目：").append(p.title()).append('\n');
        sb.append("难度：").append(p.difficulty()).append("；时间限制：").append(p.timeLimitMs())
                .append(" ms；内存限制：").append(p.memoryLimitMb()).append(" MB\n\n");
        section(sb, "题目描述", p.description());
        section(sb, "输入格式", p.inputFormat());
        section(sb, "输出格式", p.outputFormat());
        List<Sample> samples = p.samples() == null ? List.of() : p.samples();
        for (int i = 0; i < samples.size(); i++) {
            sb.append("## 样例 ").append(i + 1).append("\n输入：\n```\n").append(samples.get(i).input())
                    .append("\n```\n输出：\n```\n").append(samples.get(i).output()).append("\n```\n\n");
        }
        section(sb, "提示", p.hint());
        section(sb, "官方题解（只供你参考，用自己的话讲解，不要原样照抄给学生）", p.editorial());
        return sb.toString();
    }

    static String userMessage(ProblemInfo problem, TutorMode mode, Language language, String code, JudgeInfo judge,
                              String question) {
        StringBuilder sb = new StringBuilder("【").append(mode.label()).append("】\n");
        sb.append("我使用的语言：").append(language == null ? "还没选" : language.displayName()).append("\n\n");
        if (isBlank(code)) {
            sb.append("我还没有写代码。\n\n");
        } else {
            sb.append("我现在的代码：\n```").append(language == null ? "" : language.fence()).append('\n')
                    .append(truncate(code, MAX_CODE_CHARS)).append("\n```\n\n");
        }
        String summary = summarize(judge, problem.timeLimitMs(), problem.memoryLimitMb());
        if (summary != null) {
            sb.append("最近一次提交的评测结果：").append(summary).append("\n\n");
        }
        sb.append(isBlank(question) ? mode.defaultQuestion() : question.trim());
        return sb.toString();
    }

    static String summarize(JudgeInfo j, int timeLimitMs, int memoryLimitMb) {
        if (j == null) {
            return null;
        }
        if (!"FINISHED".equals(j.status())) {
            return "还在评测中";
        }
        StringBuilder sb = new StringBuilder(Verdicts.name(j.verdict())).append("（").append(j.verdict()).append("）");
        if (Verdicts.CE.equals(j.verdict())) {
            sb.append("\n编译信息：\n```\n").append(truncate(j.compileMessage(), 2000)).append("\n```");
        } else if (!Verdicts.AC.equals(j.verdict()) && !Verdicts.SE.equals(j.verdict())) {
            sb.append("，通过 ").append(j.passed()).append('/').append(j.total()).append(" 个测试点");
            List<CaseResult> cases = j.cases() == null ? List.of() : j.cases();
            for (int i = 0; i < cases.size(); i++) {
                CaseResult c = cases.get(i);
                if (Verdicts.AC.equals(c.verdict())) {
                    continue;
                }
                sb.append("；第一个没通过的是第 ").append(i + 1).append(" 个测试点：").append(Verdicts.name(c.verdict()));
                switch (c.verdict()) {
                    case "TLE" -> sb.append("（用时超过 ").append(timeLimitMs).append(" ms 的限制）");
                    case "MLE" -> sb.append("（内存 ").append(c.memoryKb() / 1024).append(" MB，限制 ")
                            .append(memoryLimitMb).append(" MB）");
                    default -> {
                    }
                }
                if (!isBlank(c.message())) {
                    sb.append("\n错误信息：\n```\n").append(truncate(c.message(), 800)).append("\n```");
                }
                break;
            }
        }
        if (j.codeChanged()) {
            sb.append("\n（这是最近一次提交时的结果，之后代码可能已经改过）");
        }
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, String content) {
        if (!isBlank(content)) {
            sb.append("## ").append(title).append('\n').append(content.trim()).append("\n\n");
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "\n…（内容过长，已截断）";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
