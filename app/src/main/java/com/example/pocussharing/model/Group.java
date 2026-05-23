package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

public class Group {
    private String groupId;
    private String groupName;
    private String groupCode;
    private int maxMembers;
    private String adminId;
    private java.util.List<String> memberIds;
    private Timestamp createdAt;

    public Group() {}

    public Group(String groupName, String groupCode, String adminId) {
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.adminId = adminId;
        this.memberIds = new java.util.ArrayList<>();
        this.memberIds.add(adminId);
        this.maxMembers = 12;
        this.createdAt = Timestamp.now();
    }

    // Getters and Setters
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public java.util.List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(java.util.List<String> memberIds) { this.memberIds = memberIds; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}