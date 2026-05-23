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
        WriteBatch batch = db.batch();
        
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.set(groupRef.collection("members").document(userId), member);
        batch.update(groupRef, "memberIds", FieldValue.arrayUnion(userId));
        
        return batch.commit();
    }

    public Task<QuerySnapshot> getUserGroups(String userId) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .get();
    }

    public ListenerRegistration getUserGroupsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .addSnapshotListener(listener);
    }

    public Task<QuerySnapshot> findGroupByCode(String code) {
        return db.collection(GROUPS_COLLECTION)
                .whereEqualTo("groupCode", code)
                .limit(1)
                .get();
    }

    public Task<Void> leaveGroup(String groupId, String userId) {
        WriteBatch batch = db.batch();
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.delete(groupRef.collection("members").document(userId));
        batch.update(groupRef, "memberIds", FieldValue.arrayRemove(userId));
        return batch.commit();
    }

    public Task<Void> updateGroup(String groupId, Map<String, Object> updates) {
        return db.collection(GROUPS_COLLECTION).document(groupId).update(updates);
    }

    public Task<Void> deleteGroup(String groupId) {
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        
        // Delete all members in subcollection first
        return groupRef.collection("members").get().continueWithTask(task -> {
            WriteBatch batch = db.batch();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult()) {
                    batch.delete(doc.getReference());
                }
            }
            batch.delete(groupRef);
            return batch.commit();
        });
    }

    public Task<DocumentSnapshot> getGroup(String groupId) {
        return db.collection(GROUPS_COLLECTION).document(groupId).get();
    }

    // --- Timer Log & Stats Operations ---
    public Task<Void> saveTimerLog(TimerLog log) {
        DocumentReference logRef = db.collection(TIMER_LOGS_COLLECTION).document();
        log.setLogId(logRef.getId());
        
        WriteBatch batch = db.batch();
        batch.set(logRef, log);

        // Update Daily Stats for User
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DocumentReference userStatRef = db.collection(USERS_COLLECTION).document(log.getUser_id())
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
                .collection("user_stats").document(log.getUser_id());
            
            batch.set(groupStatRef, new HashMap<String, Object>() {{
                put("focusSeconds", FieldValue.increment(focusInc));
                put("updatedAt", FieldValue.serverTimestamp());
            }}, SetOptions.merge());
        }

        return batch.commit();
    }

    public Task<Integer> getDailyFocusTime(String userId) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return db.collection(USERS_COLLECTION).document(userId)
                .collection("daily_stats").document(dateStr)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long focusSec = task.getResult().getLong("totalFocusSec");
                        return focusSec != null ? focusSec.intValue() : 0;
                    }
                    return 0;
                });
    }

    public Task<QuerySnapshot> getRecentTimerLogs(String userId) {
        return db.collection(TIMER_LOGS_COLLECTION)
                .whereEqualTo("user_id", userId)
                .get();
    }

    public ListenerRegistration getTimerLogsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(TIMER_LOGS_COLLECTION)
                .whereEqualTo("user_id", userId)
                .addSnapshotListener(listener);
    }
}