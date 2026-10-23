package com.khmermart.service;

import com.khmermart.model.response.SearchSuggestDto;

public interface SearchService {
  SearchSuggestDto suggest(String q);
}
