package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

public class GroupMember {
    private String userId;
    private String role; // ADMIN, MEMBER
    private Timestamp joinedAt;

    public GroupMember() {}

    public GroupMember(String userId, String role) {
        this.userId = userId;
        this.role = role;
        this.joinedAt = Timestamp.now();
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
}