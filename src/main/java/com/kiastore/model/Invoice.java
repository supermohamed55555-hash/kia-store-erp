package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a customer sales invoice.
 */
public class Invoice {

    public enum Status { ACTIVE, CANCELLED, RETURNED }
    public enum PaymentMethod { CASH, CARD, OTHER, CREDIT }

    private int id;
    private String invoiceNumber;
    private String customerName;
    private String customerPhone;
    private double totalAmount;
    private double discount;
    private double finalAmount;
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    private double amountPaid;
    private double amountDue;
    private Status status = Status.ACTIVE;
    private String notes;
    private Integer createdBy;
    private LocalDateTime createdAt;

    public Invoice() {}

    public int getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public double getTotalAmount() { return totalAmount; }
    public double getDiscount() { return discount; }
    public double getFinalAmount() { return finalAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public double getAmountPaid() { return amountPaid; }
    public double getAmountDue() { return amountDue; }
    public Status getStatus() { return status; }
    public String getNotes() { return notes; }
    public Integer getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public void setAmountDue(double amountDue) { this.amountDue = amountDue; }
    public void setStatus(Status status) { this.status = status; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
