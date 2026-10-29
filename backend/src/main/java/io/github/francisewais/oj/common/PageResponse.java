package io.github.francisewais.oj.common;

import java.util.List;

import org.springframework.data.domain.Page;

/** 分页结果，page 从 1 开始。 */
public record PageResponse<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }
}
