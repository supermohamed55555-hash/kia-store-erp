package com.kiastore.dao;

import com.kiastore.model.Part;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PartDao extends BaseDao<Part> {

    @Override
    protected String table() {
        return "parts";
    }

    @Override
    protected String[] columns() {
        return new String[]{
            "part_type", "location", "car_name", "car_model", "manufacturer",
            "full_name", "part_number", "part_number_normalized", "internal_code", "barcode",
            "sale_price", "purchase_price", "min_stock", "current_stock", "images", "description",
            "compatible_cars", "alternatives", "is_active", "created_by"
        };
    }

    @Override
    protected Part extract(ResultSet rs) throws SQLException {
        Part p = new Part();
        p.setId(rs.getInt("id"));
        p.setPartType(rs.getString("part_type"));
        p.setLocation(rs.getString("location"));
        p.setCarName(rs.getString("car_name"));
        p.setCarModel(rs.getString("car_model"));
        p.setManufacturer(rs.getString("manufacturer"));
        p.setFullName(rs.getString("full_name"));
        p.setPartNumber(rs.getString("part_number"));
        p.setPartNumberNormalized(rs.getString("part_number_normalized"));
        p.setInternalCode(rs.getString("internal_code"));
        p.setBarcode(rs.getString("barcode"));
        p.setSalePrice(rs.getDouble("sale_price"));
        p.setPurchasePrice(rs.getDouble("purchase_price"));
        p.setMinStock(rs.getInt("min_stock"));
        p.setCurrentStock(rs.getInt("current_stock"));
        p.setImages(rs.getString("images"));
        p.setDescription(rs.getString("description"));
        p.setCompatibleCars(rs.getString("compatible_cars"));
        p.setAlternatives(rs.getString("alternatives"));
        p.setActive(rs.getBoolean("is_active"));
        
        int creator = rs.getInt("created_by");
        p.setCreatedBy(rs.wasNull() ? null : creator);
        
        if (rs.getTimestamp("created_at") != null) {
            p.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        }
        if (rs.getTimestamp("updated_at") != null) {
            p.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        }
        return p;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, Part p) throws SQLException {
        ps.setString(1, p.getPartType());
        ps.setString(2, p.getLocation());
        ps.setString(3, p.getCarName());
        ps.setString(4, p.getCarModel());
        ps.setString(5, p.getManufacturer());
        ps.setString(6, p.getFullName());
        ps.setString(7, p.getPartNumber());
        ps.setString(8, p.getPartNumberNormalized());
        ps.setString(9, p.getInternalCode());
        ps.setString(10, p.getBarcode());
        ps.setDouble(11, p.getSalePrice());
        ps.setDouble(12, p.getPurchasePrice());
        ps.setInt(13, p.getMinStock());
        ps.setInt(14, p.getCurrentStock());
        ps.setString(15, p.getImages());
        ps.setString(16, p.getDescription());
        ps.setString(17, p.getCompatibleCars());
        ps.setString(18, p.getAlternatives());
        ps.setBoolean(19, p.isActive());
        if (p.getCreatedBy() == null) {
            ps.setNull(20, java.sql.Types.INTEGER);
        } else {
            ps.setInt(20, p.getCreatedBy());
        }
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, Part p) throws SQLException {
        bindInsert(ps, p);
        ps.setInt(21, p.getId());
    }

    @Override
    protected int idOf(Part p) {
        return p.getId();
    }

    @Override
    protected void setId(Part p, int id) {
        p.setId(id);
    }

    public List<Part> findLowStock() {
        String sql = "SELECT * FROM parts WHERE current_stock <= min_stock AND is_active = TRUE ORDER BY current_stock ASC";
        return queryList(sql);
    }

    public List<Part> findActive() {
        String sql = "SELECT * FROM parts WHERE is_active = TRUE ORDER BY full_name";
        return queryList(sql);
    }

    /**
     * Pagination helper for lazy loading.
     * @param offset  number of rows to skip
     * @param limit   max rows to return (use Integer.MAX_VALUE for "all remaining")
     */
    public List<Part> findActivePaged(int offset, int limit) {
        String sql = "SELECT * FROM parts WHERE is_active = TRUE ORDER BY full_name LIMIT ? OFFSET ?";
        List<Part> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit == Integer.MAX_VALUE ? Integer.MAX_VALUE : limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(extract(rs));
            }
        } catch (SQLException e) {
            throw new DaoException("findActivePaged failed", e);
        }
        return out;
    }


    public int countAll() {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM parts WHERE is_active = TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DaoException("countAll failed", e);
        }
    }

    public double getInventoryValue() {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement("SELECT SUM(current_stock * sale_price) FROM parts WHERE is_active = TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            throw new DaoException("getInventoryValue failed", e);
        }
    }

    public boolean decrementStock(int partId, int qty) {
        String sql = "UPDATE parts SET current_stock = current_stock - ? WHERE id = ? AND current_stock >= ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, partId);
            ps.setInt(3, qty);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DaoException("decrementStock failed", e);
        }
    }

    public void incrementStock(int partId, int qty) {
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE parts SET current_stock = current_stock + ? WHERE id = ?")) {
            ps.setInt(1, qty);
            ps.setInt(2, partId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("incrementStock failed", e);
        }
    }

    public void updateImages(int partId, String imagesJson) {
        String sql = "UPDATE parts SET images = ? WHERE id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, imagesJson);
            ps.setInt(2, partId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("updateImages failed", e);
        }
    }

    private List<Part> queryList(String sql) {
        List<Part> out = new ArrayList<>();
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(extract(rs));
        } catch (SQLException e) {
            throw new DaoException("queryList failed", e);
        }
        return out;
    }

    /**
     * Finds a part by exact barcode match or normalized part number match.
     * Used for barcode scanner input detection.
     */
    public Part findByBarcodeOrNormalizedNumber(String normalized) {
        String sql = "SELECT * FROM parts WHERE (barcode = ? OR part_number_normalized = ?) AND is_active = true LIMIT 1";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalized);
            ps.setString(2, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return extract(rs);
            }
        } catch (SQLException e) {
            throw new DaoException("findByBarcodeOrNormalizedNumber failed", e);
        }
        return null;
    }
}
