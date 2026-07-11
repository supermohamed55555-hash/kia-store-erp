package com.kiastore.service;

import com.kiastore.dao.SupplierDao;
import com.kiastore.model.Supplier;

import java.util.List;
import java.util.Optional;

public class SupplierService {

    private final SupplierDao supplierDao;

    public SupplierService(SupplierDao supplierDao) {
        this.supplierDao = supplierDao;
    }

    public List<Supplier> all() {
        return supplierDao.findAll();
    }

    public Optional<Supplier> findById(int id) {
        return supplierDao.findById(id);
    }

    public Supplier save(Supplier s) {
        if (s.getId() > 0) {
            supplierDao.update(s);
            return s;
        } else {
            return supplierDao.insert(s);
        }
    }

    public boolean delete(int id) {
        return supplierDao.delete(id);
    }
}
