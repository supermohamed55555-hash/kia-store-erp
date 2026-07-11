package com.kiastore.app;

import com.kiastore.dao.*;
import com.kiastore.service.*;

/**
 * Minimal Manual Dependency Injection container for the KIA Store ERP.
 */
public final class AppContext {

    private static AppContext INSTANCE;

    // DAOs
    public final UserDao userDao                 = new UserDao();
    public final SupplierDao supplierDao         = new SupplierDao();
    public final PartDao partDao                 = new PartDao();
    public final BatchDao batchDao               = new BatchDao();
    public final InvoiceDao invoiceDao           = new InvoiceDao();
    public final InvoiceItemDao invoiceItemDao   = new InvoiceItemDao();
    public final ReturnDao returnDao             = new ReturnDao();
    public final AuditLogDao auditLogDao         = new AuditLogDao();
    public final SearchIndexDao searchIndexDao   = new SearchIndexDao();
    public final CustomerPaymentDao customerPaymentDao = new CustomerPaymentDao();

    // Services
    public final AuthService authService         = new AuthService(userDao);
    public final UserService userService         = new UserService(userDao);
    public final SupplierService supplierService = new SupplierService(supplierDao);
    public final PartService partService         = new PartService(partDao);
    public final AuditLogService auditLogService = new AuditLogService(auditLogDao);
    public final InvoiceService invoiceService   = new InvoiceService(invoiceDao, invoiceItemDao, partDao, returnDao);
    public final StockService stockService       = new StockService(batchDao, partDao, partService);

    private AppContext() {}

    public static synchronized AppContext get() {
        if (INSTANCE == null) {
            INSTANCE = new AppContext();
        }
        return INSTANCE;
    }
}
