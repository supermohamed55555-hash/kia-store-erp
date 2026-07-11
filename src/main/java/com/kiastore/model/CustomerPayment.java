package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a payment made by a customer toward their outstanding credit balance.
 */
public class CustomerPayment {

    public enum PaymentMethod { CASH, CARD, OTHER }

    private int id;
    private String customerName;
    private String customerPhone;
    private double amountPaid;
    private PaymentMethod paymentMethod = PaymentMethod.CASH;
    private String notes;
    private Integer createdBy;
    private LocalDateTime createdAt;

    public CustomerPayment() {}

    public int getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public double getAmountPaid() { return amountPaid; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getNotes() { return notes; }
    public Integer getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
