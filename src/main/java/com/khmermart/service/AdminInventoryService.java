package com.khmermart.service;

import com.khmermart.model.request.InventoryAdjustRequest;
import com.khmermart.model.response.InventoryDto;
import com.khmermart.model.response.InventoryMovementDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminInventoryService {

  Page<InventoryDto> getInventory(Boolean lowStock, String query, Pageable pageable);

  InventoryDto restock(Long id, InventoryAdjustRequest request, Long adminId, String ip);

  InventoryDto adjust(Long id, InventoryAdjustRequest request, Long adminId, String ip);

  List<InventoryMovementDto> getMovements(Long inventoryId);
}
