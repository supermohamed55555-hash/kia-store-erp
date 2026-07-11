package com.kiastore.model;

/**
 * A summary row for the Customer Ledger screen.
 * Aggregates invoiced credit, total paid, and remaining balance for one customer.
 */
public class CustomerLedgerRow {

    private String customerName;
    private String customerPhone;
    private double totalInvoiced;   // Sum of final_amount on CREDIT invoices (active)
    private double totalPaid;       // Sum of all customer_payments + amount_paid on invoices
    private double balance;         // totalInvoiced - totalPaid (remaining debt)

    public CustomerLedgerRow(String customerName, String customerPhone,
                             double totalInvoiced, double totalPaid) {
        this.customerName   = customerName;
        this.customerPhone  = customerPhone;
        this.totalInvoiced  = totalInvoiced;
        this.totalPaid      = totalPaid;
        this.balance        = totalInvoiced - totalPaid;
    }

    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public double getTotalInvoiced() { return totalInvoiced; }
    public double getTotalPaid() { return totalPaid; }
    public double getBalance() { return balance; }

    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public void setTotalInvoiced(double totalInvoiced) {
        this.totalInvoiced = totalInvoiced;
        this.balance = totalInvoiced - totalPaid;
    }
    public void setTotalPaid(double totalPaid) {
        this.totalPaid = totalPaid;
        this.balance = totalInvoiced - totalPaid;
    }
}
