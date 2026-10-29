package io.github.francisewais.oj.user;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record AuthRequest(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名只能包含字母、数字和下划线，长度 3-20")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 6, max = 64, message = "密码长度需要在 6-64 位之间")
            String password) {
    }

    public record PasswordChange(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 64, message = "密码长度需要在 6-64 位之间")
            String newPassword) {
    }

    public record UserView(Long id, String username, String role, int solvedCount, int submitCount,
                           Instant createdAt) {

        public static UserView of(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getRole().name(), user.getSolvedCount(),
                    user.getSubmitCount(), user.getCreatedAt());
        }
    }

    public record AuthResponse(String token, UserView user) {
    }

    public record RankItem(int rank, String username, int solvedCount, int submitCount) {
    }
}
