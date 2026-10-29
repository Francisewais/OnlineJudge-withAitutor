package io.github.francisewais.oj.common;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** 从 JWT 里取出的当前用户；未登录时为 null。 */
public record CurrentUser(long id, String username, boolean admin) {

    public static CurrentUser of(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new CurrentUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("username"),
                roles != null && roles.contains("ADMIN"));
    }

    public static CurrentUser required(Jwt jwt) {
        CurrentUser user = of(jwt);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }
}
