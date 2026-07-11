package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a returned part item from a sold invoice.
 */
public class Return {

    private int id;
    private int invoiceId;
    private int partId;
    private int quantity;
    private String reason;
    private Integer returnedBy;
    private LocalDateTime returnedAt;

    public Return() {}

    public int getId() { return id; }
    public int getInvoiceId() { return invoiceId; }
    public int getPartId() { return partId; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public Integer getReturnedBy() { return returnedBy; }
    public LocalDateTime getReturnedAt() { return returnedAt; }

    public void setId(int id) { this.id = id; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
    public void setPartId(int partId) { this.partId = partId; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setReason(String reason) { this.reason = reason; }
    public void setReturnedBy(Integer returnedBy) { this.returnedBy = returnedBy; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
}
