package com.cidas.edudoc.common.api;

import java.util.List;

/** 统一分页结果：与概要设计 §5.3 的 page/pageSize 参数约定对应。 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
}
