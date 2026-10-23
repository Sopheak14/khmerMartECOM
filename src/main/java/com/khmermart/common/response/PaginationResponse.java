package com.khmermart.common.response;

public record PaginationResponse(int page, int limit, long total, int totalPages) {}
