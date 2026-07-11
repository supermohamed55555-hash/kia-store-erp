package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents an automotive part in the shop inventory.
 */
public class Part {

    private int id;
    private String partType;
    private String location;
    private String carName;
    private String carModel;
    private String manufacturer;
    private String fullName;
    private String partNumber;
    private String partNumberNormalized;
    private String internalCode;
    private String barcode;
    private double salePrice;
    private double purchasePrice;
    private int minStock = 5;
    private int currentStock = 0;
    private String images; // JSON string
    private String description;
    private String compatibleCars; // JSON string
    private String alternatives; // JSON string
    private boolean isActive = true;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Part() {}

    public int getId() { return id; }
    public String getPartType() { return partType; }
    public String getLocation() { return location; }
    public String getCarName() { return carName; }
    public String getCarModel() { return carModel; }
    public String getManufacturer() { return manufacturer; }
    public String getFullName() { return fullName; }
    public String getPartNumber() { return partNumber; }
    public String getPartNumberNormalized() { return partNumberNormalized; }
    public String getInternalCode() { return internalCode; }
    public String getBarcode() { return barcode; }
    public double getSalePrice() { return salePrice; }
    public double getPurchasePrice() { return purchasePrice; }
    public int getMinStock() { return minStock; }
    public int getCurrentStock() { return currentStock; }
    public String getImages() { return images; }
    public String getDescription() { return description; }
    public String getCompatibleCars() { return compatibleCars; }
    public String getAlternatives() { return alternatives; }
    public boolean isActive() { return isActive; }
    public Integer getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(int id) { this.id = id; }
    public void setPartType(String partType) { this.partType = partType; }
    public void setLocation(String location) { this.location = location; }
    public void setCarName(String carName) { this.carName = carName; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public void setPartNumberNormalized(String partNumberNormalized) { this.partNumberNormalized = partNumberNormalized; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public void setSalePrice(double salePrice) { this.salePrice = salePrice; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }
    public void setMinStock(int minStock) { this.minStock = minStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public void setImages(String images) { this.images = images; }
    public void setDescription(String description) { this.description = description; }
    public void setCompatibleCars(String compatibleCars) { this.compatibleCars = compatibleCars; }
    public void setAlternatives(String alternatives) { this.alternatives = alternatives; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isLowStock() { return currentStock <= minStock; }

    @Override
    public String toString() {
        return internalCode + " - " + fullName;
    }
}
