package com.khmermart.common.api;

public record PageMeta(int page, int limit, long total, int totalPages) {
  public static PageMeta of(int page, int limit, long total) {
    int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
    return new PageMeta(page, limit, total, totalPages);
  }
}
