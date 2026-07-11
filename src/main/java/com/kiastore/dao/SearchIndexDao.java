package com.kiastore.dao;

import com.kiastore.model.SearchIndex;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SearchIndexDao extends BaseDao<SearchIndex> {

    @Override
    protected String table() {
        return "search_index";
    }

    @Override
    protected String[] columns() {
        return new String[]{"part_id", "keyword", "weight"};
    }

    @Override
    protected SearchIndex extract(ResultSet rs) throws SQLException {
        SearchIndex si = new SearchIndex();
        si.setId(rs.getInt("id"));
        si.setPartId(rs.getInt("part_id"));
        si.setKeyword(rs.getString("keyword"));
        si.setWeight(rs.getInt("weight"));
        return si;
    }

    @Override
    protected void bindInsert(PreparedStatement ps, SearchIndex si) throws SQLException {
        ps.setInt(1, si.getPartId());
        ps.setString(2, si.getKeyword());
        ps.setInt(3, si.getWeight());
    }

    @Override
    protected void bindUpdate(PreparedStatement ps, SearchIndex si) throws SQLException {
        bindInsert(ps, si);
        ps.setInt(4, si.getId());
    }

    @Override
    protected int idOf(SearchIndex si) {
        return si.getId();
    }

    @Override
    protected void setId(SearchIndex si, int id) {
        si.setId(id);
    }

    public void deleteByPart(int partId) {
        String sql = "DELETE FROM search_index WHERE part_id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, partId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DaoException("deleteByPart failed", e);
        }
    }
}
