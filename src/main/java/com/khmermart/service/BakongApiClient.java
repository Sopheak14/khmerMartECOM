package com.khmermart.service;

import com.khmermart.model.response.BakongCheckResponse;

public interface BakongApiClient {
  BakongCheckResponse checkTransactionByMd5(String md5);
}
