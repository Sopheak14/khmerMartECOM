package com.khmermart.service;

public interface InvoiceService {
  byte[] generateInvoicePdf(String orderNumber);
}
