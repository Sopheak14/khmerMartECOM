package com.khmermart.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse<T>(boolean success, T data, PaginationResponse meta, Object facets) {

  public SuccessResponse(T data) {
    this(true, data, null, null);
  }

  public SuccessResponse(T data, PaginationResponse meta) {
    this(true, data, meta, null);
  }

  public SuccessResponse(T data, PaginationResponse meta, Object facets) {
    this(true, data, meta, facets);
  }
}
