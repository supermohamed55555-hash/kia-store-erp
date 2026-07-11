package com.kiastore.service;

import com.kiastore.dao.BatchDao;
import com.kiastore.dao.PartDao;
import com.kiastore.model.Batch;
import com.kiastore.model.Part;
import com.kiastore.db.ConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StockService {

    private final BatchDao batchDao;
    private final PartDao partDao;
    private final PartService partService;

    public StockService(BatchDao batchDao, PartDao partDao, PartService partService) {
        this.batchDao = batchDao;
        this.partDao = partDao;
        this.partService = partService;
    }

    public List<Batch> getBatchesForPart(int partId) {
        return batchDao.findByPart(partId);
    }

    /**
     * Receives stock for an existing part, inserting a batch log and updating current stock.
     */
    public Batch receiveStock(Batch batch) throws SQLException {
        Connection c = ConnectionFactory.borrow();
        try {
            c.setAutoCommit(false);

            // 1. Save the batch log
            Batch saved = batchDao.insert(batch);

            // 2. Increment stock in the parts table
            partDao.incrementStock(batch.getPartId(), batch.getQuantity());

            c.commit();
            return saved;
        } catch (Exception e) {
            c.rollback();
            throw new SQLException("فشل تسجيل عملية استلام المخزن: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
            c.close();
        }
    }

    /**
     * Checks if a part being created might already exist in the inventory.
     * Matches by normalized part number, barcode, internal code, or similar name structure.
     */
    public List<Part> findSimilarParts(Part newPart) {
        List<Part> allParts = partDao.findAll().stream()
                .filter(Part::isActive)
                .collect(Collectors.toList());

        List<Part> matches = new ArrayList<>();

        String newPartNoNorm = newPart.getPartNumber() != null ? newPart.getPartNumber().replaceAll("[^a-zA-Z0-9]", "").toLowerCase() : "";
        String newBarcode = newPart.getBarcode() != null ? newPart.getBarcode().trim() : "";
        String newInternalCode = newPart.getInternalCode() != null ? newPart.getInternalCode().trim().toLowerCase() : "";

        for (Part p : allParts) {
            // 1. Exact match on barcode
            if (!newBarcode.isEmpty() && newBarcode.equalsIgnoreCase(p.getBarcode())) {
                matches.add(p);
                continue;
            }

            // 2. Exact match on normalized part number
            String pNoNorm = p.getPartNumberNormalized() != null ? p.getPartNumberNormalized() : "";
            if (!newPartNoNorm.isEmpty() && newPartNoNorm.equalsIgnoreCase(pNoNorm)) {
                matches.add(p);
                continue;
            }

            // 3. Exact match on internal code
            if (!newInternalCode.isEmpty() && newInternalCode.equalsIgnoreCase(p.getInternalCode())) {
                matches.add(p);
                continue;
            }

            // 4. Similarity match on name, car make, and manufacturer
            boolean sameType = newPart.getPartType() != null && newPart.getPartType().equalsIgnoreCase(p.getPartType());
            boolean sameCarName = newPart.getCarName() != null && newPart.getCarName().equalsIgnoreCase(p.getCarName());
            boolean sameCarModel = newPart.getCarModel() != null && newPart.getCarModel().equalsIgnoreCase(p.getCarModel());
            boolean sameManufacturer = newPart.getManufacturer() != null && newPart.getManufacturer().equalsIgnoreCase(p.getManufacturer());

            if (sameType && sameCarName && sameCarModel && sameManufacturer) {
                matches.add(p);
            }
        }

        return matches;
    }
}
