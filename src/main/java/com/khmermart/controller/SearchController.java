package com.khmermart.controller;

import com.khmermart.common.response.SuccessResponse;
import com.khmermart.model.response.SearchSuggestDto;
import com.khmermart.service.SearchService;
import com.khmermart.util.ApiResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@Tag(name = "Search", description = "Typeahead and search suggestion APIs")
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @GetMapping("/suggest")
  @Operation(summary = "Live search autocomplete suggestions")
  public SuccessResponse<SearchSuggestDto> suggest(@RequestParam("q") String q) {
    return ApiResponseUtil.success(searchService.suggest(q));
  }
}
