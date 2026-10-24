package com.khmermart.service.impl;

import com.khmermart.common.exception.CustomException;
import com.khmermart.common.exception.ErrorCode;
import com.khmermart.model.entity.Inventory;
import com.khmermart.model.entity.InventoryMovement;
import com.khmermart.model.entity.Product;
import com.khmermart.model.enumerate.InventoryMovementType;
import com.khmermart.model.request.InventoryAdjustRequest;
import com.khmermart.model.response.InventoryDto;
import com.khmermart.model.response.InventoryMovementDto;
import com.khmermart.repository.InventoryMovementRepository;
import com.khmermart.repository.InventoryRepository;
import com.khmermart.repository.ProductRepository;
import com.khmermart.service.AdminInventoryService;
import com.khmermart.service.AuditLogService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInventoryServiceImpl implements AdminInventoryService {

  private final InventoryRepository inventoryRepository;
  private final InventoryMovementRepository movementRepository;
  private final ProductRepository productRepository;
  private final AuditLogService auditLogService;

  public AdminInventoryServiceImpl(
      InventoryRepository inventoryRepository,
      InventoryMovementRepository movementRepository,
      ProductRepository productRepository,
      AuditLogService auditLogService) {
    this.inventoryRepository = inventoryRepository;
    this.movementRepository = movementRepository;
    this.productRepository = productRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<InventoryDto> getInventory(Boolean lowStock, String query, Pageable pageable) {
    Page<Inventory> page;
    if (Boolean.TRUE.equals(lowStock)) {
      page = inventoryRepository.findLowStock(pageable);
    } else {
      page = inventoryRepository.findAll(pageable);
    }
    return page.map(this::toDto);
  }

  @Override
  @Transactional
  public InventoryDto restock(Long id, InventoryAdjustRequest request, Long adminId, String ip) {
    Inventory inv =
        inventoryRepository
            .findById(id)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Inventory item not found"));

    if (request.quantity() <= 0) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Restock quantity must be greater than zero");
    }

    int beforeQty = inv.getQuantityOnHand();
    boolean beforeAlerted = inv.isLowStockAlerted();

    inv.setQuantityOnHand(inv.getQuantityOnHand() + request.quantity());
    // Rule: restock resets the low-stock alert flag
    inv.setLowStockAlerted(false);
    inv.setUpdatedAt(Instant.now());
    Inventory updated = inventoryRepository.save(inv);

    String reason = request.reason() != null ? request.reason() : "Admin manual restock";
    InventoryMovement movement =
        InventoryMovement.builder()
            .inventoryId(inv.getId())
            .type(InventoryMovementType.RESTOCK)
            .quantity(request.quantity())
            .reason(reason)
            .actorId(adminId)
            .createdAt(Instant.now())
            .build();
    movementRepository.save(movement);

    auditLogService.log(
        adminId,
        "RESTOCK_INVENTORY",
        "INVENTORY",
        String.valueOf(id),
        Map.of("quantityOnHand", beforeQty, "lowStockAlerted", beforeAlerted),
        Map.of("quantityOnHand", updated.getQuantityOnHand(), "lowStockAlerted", false),
        ip);

    return toDto(updated);
  }

  @Override
  @Transactional
  public InventoryDto adjust(Long id, InventoryAdjustRequest request, Long adminId, String ip) {
    Inventory inv =
        inventoryRepository
            .findById(id)
            .orElseThrow(
                () -> new CustomException(ErrorCode.NOT_FOUND, "Inventory item not found"));

    int newQty = inv.getQuantityOnHand() + request.quantity();
    if (newQty < 0) {
      throw new CustomException(
          ErrorCode.VALIDATION_ERROR, "Inventory quantity on hand cannot become negative");
    }

    int beforeQty = inv.getQuantityOnHand();
    inv.setQuantityOnHand(newQty);
    inv.setUpdatedAt(Instant.now());
    Inventory updated = inventoryRepository.save(inv);

    String reason = request.reason() != null ? request.reason() : "Admin inventory adjustment";
    InventoryMovement movement =
        InventoryMovement.builder()
            .inventoryId(inv.getId())
            .type(InventoryMovementType.ADJUST)
            .quantity(request.quantity())
            .reason(reason)
            .actorId(adminId)
            .createdAt(Instant.now())
            .build();
    movementRepository.save(movement);

    auditLogService.log(
        adminId,
        "ADJUST_INVENTORY",
        "INVENTORY",
        String.valueOf(id),
        Map.of("quantityOnHand", beforeQty),
        Map.of("quantityOnHand", updated.getQuantityOnHand(), "adjustment", request.quantity()),
        ip);

    return toDto(updated);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InventoryMovementDto> getMovements(Long inventoryId) {
    return movementRepository.findByInventoryIdOrderByCreatedAtDesc(inventoryId).stream()
        .map(
            m ->
                new InventoryMovementDto(
                    m.getId(),
                    m.getInventoryId(),
                    m.getType(),
                    m.getQuantity(),
                    m.getReason(),
                    m.getRefOrderId(),
                    m.getActorId(),
                    m.getCreatedAt()))
        .toList();
  }

  private InventoryDto toDto(Inventory inv) {
    Product prod = productRepository.findById(inv.getProductId()).orElse(null);
    String prodName = prod != null ? prod.getName() : "Product #" + inv.getProductId();
    String sku = prod != null ? prod.getSku() : "";

    int available = inv.getQuantityOnHand() - inv.getQuantityReserved();
    return new InventoryDto(
        inv.getId(),
        inv.getProductId(),
        prodName,
        sku,
        inv.getVariantId(),
        inv.getQuantityOnHand(),
        inv.getQuantityReserved(),
        available,
        inv.getLowStockThreshold(),
        inv.isLowStockAlerted());
  }
}
