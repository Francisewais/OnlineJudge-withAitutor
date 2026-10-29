package io.github.francisewais.oj.seed;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.zip.GZIPInputStream;

import io.github.francisewais.oj.config.AppProperties;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemForm;
import io.github.francisewais.oj.problem.ProblemRepository;
import io.github.francisewais.oj.problem.ProblemService;
import io.github.francisewais.oj.user.User;
import io.github.francisewais.oj.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 首次启动时创建管理员账号，并导入 seed/problems.json 里的示例题目。 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ProblemRepository problemRepository;
    private final ProblemService problems;
    private final AppProperties props;
    private final JsonMapper json;

    public DataInitializer(UserRepository users, PasswordEncoder passwordEncoder, ProblemRepository problemRepository,
                           ProblemService problems, AppProperties props, JsonMapper json) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.problemRepository = problemRepository;
        this.problems = problems;
        this.props = props;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        createAdmin();
        importSampleProblems();
    }

    private void createAdmin() {
        String username = props.admin().username();
        if (users.existsByUsername(username)) {
            return;
        }
        String password = props.admin().password();
        if (password == null || password.isBlank()) {
            password = randomPassword();
            log.warn("已创建管理员 {}，随机密码：{}  —— 登录后可以在右上角修改密码", username, password);
        } else {
            log.info("已创建管理员 {}", username);
        }
        users.save(new User(username, passwordEncoder.encode(password), User.Role.ADMIN));
    }

    private void importSampleProblems() throws IOException {
        if (problemRepository.count() > 0) {
            return;
        }
        // 由 scripts/generate_seed.py 生成
        var resource = new ClassPathResource("seed/problems.json.gz");
        if (!resource.exists()) {
            return;
        }
        try (InputStream in = new GZIPInputStream(resource.getInputStream())) {
            List<ProblemForm> forms = json.readValue(in, new TypeReference<List<ProblemForm>>() {
            });
            forms.forEach(problems::create);
            log.info("导入了 {} 道示例题目", forms.size());
        }
    }

    private static String randomPassword() {
        String chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var random = new SecureRandom();
        var sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
