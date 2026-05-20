package com.example.pocussharing.repository;

import com.example.pocussharing.model.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FirestoreRepository {
    private FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";
    private static final String GROUPS_COLLECTION = "groups";
    private static final String TIMER_LOGS_COLLECTION = "timer_logs";

    public FirestoreRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // --- User Operations ---
    public Task<Void> saveUser(User user) {
        return db.collection(USERS_COLLECTION).document(user.getUserId()).set(user);
    }

    public Task<DocumentSnapshot> getUser(String userId) {
        return db.collection(USERS_COLLECTION).document(userId).get();
    }

    // --- Group Operations ---
    public Task<DocumentReference> createGroup(Group group) {
        return db.collection(GROUPS_COLLECTION).add(group)
            .continueWithTask(task -> {
                String groupId = task.getResult().getId();
                group.setGroupId(groupId);
                // Update doc with ID
                db.collection(GROUPS_COLLECTION).document(groupId).set(group);
                // Add creator as ADMIN member
                GroupMember admin = new GroupMember(group.getAdminId(), "ADMIN");
                return db.collection(GROUPS_COLLECTION).document(groupId)
                    .collection("members").document(group.getAdminId()).set(admin)
                    .continueWith(t -> task.getResult());
            });
    }

    public Task<Void> joinGroup(String groupId, String userId) {
        GroupMember member = new GroupMember(userId, "MEMBER");
        return db.collection(GROUPS_COLLECTION).document(groupId)
            .collection("members").document(userId).set(member);
    }

    // --- Timer Log & Stats Operations ---
    public Task<Void> saveTimerLog(TimerLog log) {
        DocumentReference logRef = db.collection(TIMER_LOGS_COLLECTION).document();
        log.setLogId(logRef.getId());
        
        WriteBatch batch = db.batch();
        batch.set(logRef, log);

        // Update Daily Stats for User
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DocumentReference userStatRef = db.collection(USERS_COLLECTION).document(log.getUserId())
            .collection("daily_stats").document(dateStr);

        int focusInc = log.getLogType().equals("FOCUS") ? log.getDurationSeconds() : 0;
        int restInc = log.getLogType().equals("REST") ? log.getDurationSeconds() : 0;

        batch.set(userStatRef, new HashMap<String, Object>() {{
            put("totalFocusSec", FieldValue.increment(focusInc));
            put("totalRestSec", FieldValue.increment(restInc));
            put("updatedAt", FieldValue.serverTimestamp());
        }}, SetOptions.merge());

        // Update Group Stats if applicable
        if (log.getGroupId() != null) {
            DocumentReference groupStatRef = db.collection(GROUPS_COLLECTION).document(log.getGroupId())
                .collection("daily_stats").document(dateStr)
                .collection("user_stats").document(log.getUserId());
            
            batch.set(groupStatRef, new HashMap<String, Object>() {{
                put("focusSeconds", FieldValue.increment(focusInc));
                put("updatedAt", FieldValue.serverTimestamp());
            }}, SetOptions.merge());
        }

        return batch.commit();
    }
}