# Pocus Sharing - Project Status & Context

This document serves as a persistent context for Gemini CLI and other AI agents to understand the project's current architecture and implemented features.

## 🚀 Core Features Implemented

### 1. Authentication
- **Kakao Login:** Integrated via Kakao SDK. Users login with their Kakao accounts.
- **Firebase Auth Bridge:** Upon Kakao login, the app performs a **Firebase Anonymous Authentication** to satisfy Firestore security rules.
- **User Profile:** User nicknames and profile image URLs from Kakao are saved to Firestore (`users` collection). Custom nicknames are preserved across logins.

### 2. Timer System (Home & Group Detail)
- **Synchronized UI:** Both `HomeFragment` and `GroupDetailActivity` feature an identical, large circular `TimerView` (300dp/250dp) with a digital countdown display.
- **Modes:** Supports **FOCUS** (pink) and **REST** (gray) modes.
- **Real-time Sync:** While a timer is running, the status (mode, time left, today's total focus time) is synced to **Firebase Realtime Database** (`group_presence/{groupId}/{userId}`).
- **Persistence:** Completed sessions are saved to Firestore (`timer_logs`).
- **Stats:** "Total Focus Time" displayed on the Home screen excludes REST periods.
- **History:** Home screen displays a scrollable table of all past timer logs, sorted newest-first.

### 3. Group Management
- **Creation:** Users can create groups with a name. A unique **6-character alphanumeric code** is generated automatically.
- **Joining:** Users can join groups by entering a 6-character code (case-insensitive).
- **Listing:** `GroupFragment` shows a real-time list of groups the user is part of.
- **Administration (Group Owners):**
  - Edit group name and maximum member limit.
  - **Kick members:** Long-press a member card in the detail view to remove them.
  - **Delete group:** Permanently remove the group and all its associated data.
- **Detail View:** Shows all members' real-time timer status and their cumulative focus time for the day.

## 🛠 Technical Architecture

- **Backend:** Firebase (Firestore for persistent data, Realtime DB for live presence, Auth for security).
- **Image Loading:** Glide (for circular Kakao profile images).
- **Layouts:** ConstraintLayout based, using custom `TimerView`.
- **Navigation:** BottomNavigationView with Fragment-based architecture.

## 📝 Current Development Context
- **Firestore Rules:** Currently set to `allow read, write: if request.auth != null;` (or `if true` for testing). Ensure field names like `user_id` match the Java model.
- **Model Classes:** 
  - `User.java`: Stores basic profile and settings.
  - `TimerLog.java`: Stores individual session records (uses `user_id`).
  - `MemberStatus.java`: DTO for Realtime DB presence sync.
  - `Group.java`: Stores group metadata and `memberIds` array.

## 📌 Next Steps / Ideas
- [ ] Implement group chat.
- [ ] Add weekly/monthly focus charts.
- [ ] Implement push notifications for group achievements.
- [ ] Refine "App Exit Prevention" logic in Settings.
