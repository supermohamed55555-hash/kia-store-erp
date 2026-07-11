package com.kiastore.model;

/**
 * Represents a single line item within a customer invoice.
 */
public class InvoiceItem {

    private int id;
    private int invoiceId;
    private int partId;
    private int quantity;
    private double unitPrice;
    private double totalPrice;

    // Transient helper fields for UI display
    private String partFullName;
    private String internalCode;
    private double purchasePrice;

    public InvoiceItem() {}

    public int getId() { return id; }
    public int getInvoiceId() { return invoiceId; }
    public int getPartId() { return partId; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getTotalPrice() { return totalPrice; }

    public String getPartFullName() { return partFullName; }
    public String getInternalCode() { return internalCode; }
    public double getPurchasePrice() { return purchasePrice; }

    public void setId(int id) { this.id = id; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
    public void setPartId(int partId) { this.partId = partId; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public void setPartFullName(String partFullName) { this.partFullName = partFullName; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public void setPurchasePrice(double purchasePrice) { this.purchasePrice = purchasePrice; }
}
