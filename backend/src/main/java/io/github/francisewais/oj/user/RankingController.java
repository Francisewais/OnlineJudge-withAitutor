package io.github.francisewais.oj.user;

import java.util.List;
import java.util.stream.IntStream;

import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.user.UserDtos.RankItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RankingController {

    private final UserRepository users;

    public RankingController(UserRepository users) {
        this.users = users;
    }

    /** 按通过题数从多到少排名，题数相同时提交次数少的在前。 */
    @GetMapping("/api/ranking")
    public PageResponse<RankItem> ranking(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.clamp(size, 1, 100);
        Sort sort = Sort.by(Sort.Order.desc("solvedCount"), Sort.Order.asc("submitCount"), Sort.Order.asc("id"));
        Page<User> result = users.findAll(PageRequest.of(Math.max(page, 1) - 1, safeSize, sort));
        int offset = (int) result.getPageable().getOffset();
        List<User> content = result.getContent();
        List<RankItem> items = IntStream.range(0, content.size())
                .mapToObj(i -> new RankItem(offset + i + 1, content.get(i).getUsername(),
                        content.get(i).getSolvedCount(), content.get(i).getSubmitCount()))
                .toList();
        return PageResponse.of(result, items);
    }
}
