package com.khmermart.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BakongCheckResponse(
    Integer responseCode, String responseMessage, Integer errorCode, BakongTxData data) {}
