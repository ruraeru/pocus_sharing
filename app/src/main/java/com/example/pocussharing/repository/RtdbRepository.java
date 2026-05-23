package com.example.pocussharing.repository;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class RtdbRepository {
    private DatabaseReference presenceRef;

    public RtdbRepository() {
        this.presenceRef = FirebaseDatabase.getInstance("https://pocus-sharing-2026-default-rtdb.firebaseio.com")
                .getReference("group_presence");
    }

    public void updateUserStatus(String groupId, String userId, boolean isFocus, long timeLeftMillis) {
        if (groupId == null || userId == null) return;

        Map<String, Object> status = new HashMap<>();
        status.put("isFocus", isFocus);
        status.put("timeLeft", timeLeftMillis);
        status.put("timestamp", System.currentTimeMillis());

        presenceRef.child(groupId).child(userId).setValue(status);
    }

    public void removeUserStatus(String groupId, String userId) {
        if (groupId == null || userId == null) return;
        presenceRef.child(groupId).child(userId).removeValue();
    }
    
    public DatabaseReference getGroupPresenceRef(String groupId) {
        return presenceRef.child(groupId);
    }
}