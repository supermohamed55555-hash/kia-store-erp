package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a log of an administrative action taken in the system.
 */
public class AuditLog {

    private int id;
    private Integer userId;
    private String userName;
    private String action;
    private String tableName;
    private Integer recordId;
    private String oldData; // JSON string
    private String newData; // JSON string
    private String ipAddress;
    private LocalDateTime createdAt;

    public AuditLog() {}

    public int getId() { return id; }
    public Integer getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAction() { return action; }
    public String getTableName() { return tableName; }
    public Integer getRecordId() { return recordId; }
    public String getOldData() { return oldData; }
    public String getNewData() { return newData; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setAction(String action) { this.action = action; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public void setRecordId(Integer recordId) { this.recordId = recordId; }
    public void setOldData(String oldData) { this.oldData = oldData; }
    public void setNewData(String newData) { this.newData = newData; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
