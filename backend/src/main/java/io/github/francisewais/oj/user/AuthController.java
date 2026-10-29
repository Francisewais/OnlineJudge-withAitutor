package io.github.francisewais.oj.user;

import io.github.francisewais.oj.common.ApiException;
import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.user.UserDtos.AuthRequest;
import io.github.francisewais.oj.user.UserDtos.AuthResponse;
import io.github.francisewais.oj.user.UserDtos.PasswordChange;
import io.github.francisewais.oj.user.UserDtos.UserView;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被占用");
        }
        User user;
        try {
            user = users.save(new User(req.username(), passwordEncoder.encode(req.password()), User.Role.USER));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被占用");
        }
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        User user = users.findByUsername(req.username())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        long id = CurrentUser.required(jwt).id();
        return users.findById(id).map(UserView::of).orElseThrow(() -> ApiException.notFound("用户"));
    }

    @PostMapping("/password")
    public void changePassword(@Valid @RequestBody PasswordChange req, @AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(CurrentUser.required(jwt).id()).orElseThrow(() -> ApiException.notFound("用户"));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        users.save(user);
    }
}
