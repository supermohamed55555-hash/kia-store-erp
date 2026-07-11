package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a batch stock entry of a part from a supplier.
 */
public class Batch {

    private int id;
    private int partId;
    private int supplierId;
    private int quantity;
    private double purchasePrice;
    private String purchaseInvoiceNumber;
    private LocalDateTime receivedAt;
    private Integer receivedBy;
    private String notes;

    public Batch() {}

    public int getId() { return id; }
    public int getPartId() { return partId; }
    public int getSupplierId() { return supplierId; }
    public int getQuantity() { return quantity; }
    public double getPurchasePrice() { return purchasePrice; }
    public String getPurchaseInvoiceNumber() { return purchaseInvoiceNumber; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public Integer getReceivedBy() { return receivedBy; }
    public String getNotes() { return notes; }

    public void setId(int id) { this.id = id; }
    public void setPartId(int partId) { this.partId = partId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }
    public void setPurchaseInvoiceNumber(String purchaseInvoiceNumber) { this.purchaseInvoiceNumber = purchaseInvoiceNumber; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public void setReceivedBy(Integer receivedBy) { this.receivedBy = receivedBy; }
    public void setNotes(String notes) { this.notes = notes; }
}
