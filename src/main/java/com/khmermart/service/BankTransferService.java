package com.khmermart.service;

import com.khmermart.model.response.OrderDto;
import org.springframework.web.multipart.MultipartFile;

public interface BankTransferService {
  OrderDto uploadSlip(Long userId, String orderNumber, MultipartFile file);

  OrderDto approveBankTransfer(Long paymentId, Long adminUserId);
}
