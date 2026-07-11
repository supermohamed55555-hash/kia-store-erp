package com.kiastore.dao;

import com.kiastore.model.CustomerLedgerRow;
import com.kiastore.model.CustomerPayment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the customer_payments table and customer ledger aggregation queries.
 */
public class CustomerPaymentDao extends BaseDao<CustomerPayment> {

    @Override
    protected String table() {
        return "customer_payments";
    }

    @Override
    protected String[] columns() {
        return new String[]{"customer_name", "customer_phone", "amount_paid", "payment_method", "notes", "created_by"};
    }

    @Override
    protected CustomerPayment extract(ResultSet rs) throws SQLException {
        CustomerPayment cp = new CustomerPayment();
        cp.setId(rs.getInt("id"));
        cp.setCustomerName(rs.getString("customer_name"));
        cp.setCustomerPhone(rs.getString("customer_phone"));
        cp.setAmountPaid(rs.getDouble("amount_paid"));
        String pm = rs.getString("payment_method");
        if (pm != null) cp.setPaymentMethod(CustomerPayment.PaymentMethod.valueOf(pm.toUpperCase()));
        cp.setNotes(rs.getString("notes"));
        int cb = rs.getInt("created_by");
        cp.setCreatedBy(rs.wasNull() ? null : cb);
        if (rs.getTimestamp("created_at") != null) {
            cp.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        return cp;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, CustomerPayment cp) throws SQLException {
        ps.setString(1, cp.getCustomerName());
        ps.setString(2, cp.getCustomerPhone());
        ps.setDouble(3, cp.getAmountPaid());
        ps.setString(4, cp.getPaymentMethod().name().toLowerCase());
        ps.setString(5, cp.getNotes());
        if (cp.getCreatedBy() == null) {
            ps.setNull(6, java.sql.Types.INTEGER);
        } else {
            ps.setInt(6, cp.getCreatedBy());
        }
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, CustomerPayment cp) throws SQLException {
        bindInsert(ps, cp);
        ps.setInt(7, cp.getId());
    }

    @Override
    protected int idOf(CustomerPayment cp) { return cp.getId(); }

    @Override
    protected void setId(CustomerPayment cp, int id) { cp.setId(id); }

    /**
     * Returns all payments for a given customer phone number, sorted by date desc.
     */
    public List<CustomerPayment> findByPhone(String phone) {
        List<CustomerPayment> list = new ArrayList<>();
        String sql = "SELECT * FROM customer_payments WHERE customer_phone = ? ORDER BY created_at DESC";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("findByPhone failed", e);
        }
        return list;
    }

    /**
     * Returns total payments made by a customer phone number.
     */
    public double totalPaidByPhone(String phone) {
        String sql = "SELECT COALESCE(SUM(amount_paid), 0) FROM customer_payments WHERE customer_phone = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            throw new DaoException("totalPaidByPhone failed", e);
        }
        return 0;
    }

    /**
     * Returns a ledger summary per customer: aggregates credit invoices and payments.
     * Each row contains: customer_name, customer_phone, total_invoiced (credit), total_paid, balance.
     */
    public List<CustomerLedgerRow> getLedgerSummary() {
        // Union approach: get all customers who had credit invoices, then subtract payments
        String sql =
            "SELECT i.customer_name, i.customer_phone, " +
            "  COALESCE(SUM(i.final_amount), 0) AS total_invoiced, " +
            "  COALESCE(SUM(i.amount_paid), 0) AS paid_on_invoice " +
            "FROM invoices i " +
            "WHERE i.payment_method = 'credit' AND i.status = 'active' " +
            "GROUP BY i.customer_phone, i.customer_name";

        List<CustomerLedgerRow> rows = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String phone = rs.getString("customer_phone");
                String name  = rs.getString("customer_name");
                double invoiced  = rs.getDouble("total_invoiced");
                double paidOnInv = rs.getDouble("paid_on_invoice");

                // Also add subsequent payments from customer_payments table
                double laterPayments = totalPaidByPhone(phone);
                double totalPaid = paidOnInv + laterPayments;

                rows.add(new CustomerLedgerRow(name, phone, invoiced, totalPaid));
            }
        } catch (SQLException e) {
            throw new DaoException("getLedgerSummary failed", e);
        }
        return rows;
    }
}
