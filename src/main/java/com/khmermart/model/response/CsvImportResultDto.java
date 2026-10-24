package com.khmermart.model.response;

import java.util.List;

public record CsvImportResultDto(int imported, List<CsvRowError> errors) {
  public record CsvRowError(int row, String message) {}
}
