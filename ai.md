# Pocus Sharing - AI 활용 및 디버깅 보고서

## 1. AI 활용 과정 (구현)

### 1) 실시간 타이머 및 그룹 공유 로직 구현 (하이브리드 데이터베이스 설계)

- **활용 리소스**: Gemini CLI (Auto-Edit 모드), Firebase 공식 문서
- **문제 상황**: 사용자가 타이머를 돌릴 때 그 상태를 실시간으로 그룹원들과 공유해야 했음. 처음에는 모든 데이터를 Firestore에 실시간으로 쓰려고 했으나, 1초마다 모든 멤버가 쓰기 연산을 발생시킬 경우 발생하는 엄청난 비용과 지연(Latency) 문제가 우려됨. 영구 저장과 실시간 공유를 동시에 만족하는 구조가 필요했음.
- **초기 Prompt/검색어**: _"Firebase Firestore와 Realtime Database를 병행해서 사용하고 싶어. 타이머가 작동 중일 때의 1초 단위 데이터는 RTDB에, 세션이 종료된 후의 최종 기록은 Firestore에 저장하는 구조로 Repository와 모델을 설계해줘."_
- **해결 과정 및 수정 내역**
  - AI가 `FirestoreRepository`와 `RtdbRepository`로 역할을 명확히 분리하는 아키텍처를 제안함.
  - RTDB에서는 `/group_presence/{groupId}/{userId}` 경로를 사용하여 데이터 트래픽을 최소화하고, Firestore에서는 `WriteBatch`와 `FieldValue.increment`를 사용해 데이터 정합성을 유지하며 일일 통계를 합산하는 로직을 제공받음.
  - AI의 조언에 따라 `MemberStatus` DTO를 정의하고, `GroupDetailActivity`에서 `ValueEventListener`를 통해 실시간으로 랭킹을 정렬(`memberList.sort()`)하여 UI를 갱신하는 방식으로 최종 구현함.
- **핵심 코드 (RTDB 실시간 동기화 & Firestore 배치 저장)**:
```java
// RtdbRepository.java: 1초 단위 고빈도 데이터 처리
public void updateUserStatus(String groupId, String userId, String name, boolean isFocus, long timeLeftMillis, long todayFocusTimeMillis) {
    Map<String, Object> status = new HashMap<>();
    status.put("isFocus", isFocus);
    status.put("timeLeft", timeLeftMillis);
    status.put("todayFocusTime", todayFocusTimeMillis);
    // 실시간 DB의 특정 노드에 덮어쓰기 (비용 저렴, 지연 시간 낮음)
    ref.child(groupId).child(userId).setValue(status);
}

// FirestoreRepository.java: 세션 종료 후 영구 저장 및 통계 합산
public Task<Void> saveTimerLog(TimerLog log) {
    WriteBatch batch = db.batch();
    // 1. 개별 로그 저장
    batch.set(db.collection("timer_logs").document(), log);
    // 2. 일일 통계 원자적 증가 (Atomic Increment)
    DocumentReference statRef = db.collection("users").document(log.getUser_id())
        .collection("daily_stats").document(currentDate);
    batch.set(statRef, new HashMap<String, Object>() {{
        put("totalFocusSec", FieldValue.increment(log.getDurationSeconds()));
    }}, SetOptions.merge());
    return batch.commit();
}
```
- **이해 및 성찰**
  - 처음에는 그냥 편하게 한 곳에 다 저장하려고 했는데, 서비스가 커졌을 때의 비용이나 속도까지 생각해서 아키텍처를 짜야 한다는 걸 배웠어요.
  - 특히 여러 작업을 하나로 묶어서 처리하는 `WriteBatch`를 써보면서, 데이터가 꼬이지 않게 '원자성'을 지키는 게 실제 서비스에서 얼마나 중요한지 체감했습니다.

### 2) 커스텀 타이머 UI (원형 다이얼 인터랙션) 구현

- **활용 리소스**: Gemini CLI
- **문제 상황**: 안드로이드 기본 위젯에는 사용자가 직접 손가락으로 다이얼을 돌려 시간을 설정하는 UI가 없었음. 시각적으로 직관적인 원형 타이머를 구현하기 위해 `Canvas` 드로잉과 복잡한 터치 좌표 계산이 필요했음.
- **초기 Prompt/검색어**: _"안드로이드에서 원형 다이얼 모양의 타이머 커스텀 뷰를 만들고 싶어. 사용자가 테두리를 터치해서 드래그하면 각도에 따라 시간이 설정되고, 진행률에 따라 색이 차오르는 onDraw 로직을 작성해줘."_
- **해결 과정 및 수정 내역**
  - AI가 `onDraw`에서 `drawArc`와 `drawText`(분 단위 눈금)를 사용하여 원형 눈금을 그리는 기초 코드를 제공함.
  - 특히 `onTouchEvent`에서 터치된 (x, y) 좌표를 `Math.atan2` 함수를 이용해 각도로 변환하고, 이를 다시 0.0 ~ 1.0 사이의 `progress` 값으로 치환하는 핵심 알고리즘을 제안받음.
  - 사용자가 다이얼을 돌릴 때 '분' 단위로 딱딱 끊기는 느낌을 주기 위해 `Math.round`를 활용한 스냅(Snap) 기능과 햅틱 피드백(`HapticFeedbackConstants.CLOCK_TICK`)을 추가하여 완성도를 높임.
- **핵심 코드 (삼각함수 기반 터치 좌표 변환 & Canvas 드로잉)**:
```java
// TimerView.java: 터치 좌표를 각도로 변환하는 핵심 로직
@Override
public boolean onTouchEvent(MotionEvent event) {
    float x = event.getX();
    float y = event.getY();
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;

    // 1. 탄젠트 역함수를 이용해 터치 좌표의 각도(Radian) 계산 후 Degree로 변환
    double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX)) + 90;
    if (angle < 0) angle += 360;

    // 2. 각도를 0.0~1.0 사이의 progress로 변환 및 분 단위 스냅(Snap) 적용
    float rawProgress = (float) (angle / 360f);
    int minutes = Math.round(rawProgress * 60);
    this.progress = minutes / 60f;

    // 3. 햅틱 피드백 제공 및 뷰 갱신
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    invalidate();
    return true;
}
```
- **이해 및 성찰**
  - 수학 시간에나 보던 삼각함수를 안드로이드 좌표 계산에 실제로 써보니까 정말 신기하고 재밌었어요.
  - 터치한 위치를 각도로 바꾸고 그걸 다시 시간 데이터로 변환하는 과정이 꽤 복잡했는데, AI 도움으로 원리를 이해하고 나니까 커스텀 뷰 제작에 자신감이 생겼습니다.

### 3) 카카오 로그인 기반의 Firebase 인증 브릿지 설계

- **활용 리소스**: Kakao SDK Docs, Firebase Auth Docs, Claude 3.5
- **문제 상황**: 프로젝트 초기 설계 시 로그인은 카카오 SDK를 사용하기로 했으나, Firestore의 보안 규칙(Security Rules)은 Firebase Auth 인증 객체가 있어야만 `request.auth != null` 조건을 통과할 수 있었음. 카카오 계정과 Firebase 인증을 연동하는 방법이 모호했음.
- **초기 Prompt/검색어**: _"우리 앱은 카카오 로그인을 쓰는데, Firestore에 데이터를 쓰려면 Firebase 인증이 필요해. 카카오 로그인 성공 직후에 사용자 몰래 Firebase 인증을 수행하는 가장 간단한 방법이 뭐야?"_
- **해결 과정 및 수정 내역**
  - AI가 카카오 로그인 콜백 내부에서 Firebase의 `signInAnonymously()`를 호출하는 '익명 인증 브릿지' 방식을 제안함.
  - 카카오에서 받은 닉네임과 프로필 사진을 `User` 모델에 담아 Firestore의 `users` 컬렉션에 UID와 매핑하여 저장하는 흐름을 설계함.
  - 이 과정을 통해 카카오의 고유 ID를 Firebase UID의 별도 필드로 관리하면서 보안과 편의성을 동시에 잡음.
- **핵심 코드 (인증 연동 및 데이터 매핑)**:
```java
// LoginActivity.java: 카카오 성공 후 Firebase 익명 인증 수행
private void firebaseSignIn() {
    mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
        if (task.isSuccessful()) {
            // Firebase UID 획득 후 카카오 프로필 정보와 함께 저장
            fetchUserInfo(); 
        }
    });
}

private void fetchUserInfo() {
    UserApiClient.getInstance().me((user, error) -> {
        String uid = mAuth.getCurrentUser().getUid(); // Firebase UID
        String kakaoId = String.valueOf(user.getId()); // 카카오 고유 ID
        
        // Firestore 'users' 컬렉션에 Firebase UID를 문서 ID로 사용해 저장
        User firestoreUser = new User(uid, kakaoId, user.getNickname());
        repository.saveUser(firestoreUser);
    });
}
```
- **이해 및 성찰**
  - 카카오 로그인만 구현하면 끝날 줄 알았는데, 파이어베이스 보안 규칙을 지키기 위해 인증을 한 번 더 엮어야 한다는 걸 처음 알게 되었어요.
  - 두 플랫폼의 인증 시스템을 연결해 보면서 전체적인 데이터 흐름과 보안의 중요성을 더 넓게 볼 수 있는 시야가 생긴 것 같습니다.

## 2. 가장 어려웠던 버그 해결 및 디버깅 과정

### 1) 그룹 상세 화면 진입 시 Toolbar 클래스 캐스팅 에러 (ClassCastException)

- **활용 리소스**: Android Studio Logcat, Gemini
- **문제 상황**: 그룹 목록에서 아이템을 클릭하면 상세 화면으로 넘어가야 하는데, 화면이 뜨기도 전에 앱이 강제로 종료되는 버그가 발생함. 빌드 에러는 없었으나 실행 시에만 터지는 런타임 에러였음.
- **초기 Prompt/에러 입력**: _"java.lang.ClassCastException: android.widget.Toolbar cannot be cast to androidx.appcompat.widget.Toolbar 에러가 나면서 앱이 죽어. 코드에서는 분명 androidx 툴바를 임포트했는데 왜 이런 에러가 나는 거야?"_
- **AI의 분석 및 디버깅 과정**
  - **원인 분석**: AI가 Logcat의 스택 트레이스를 분석하여, 자바 코드(`GroupDetailActivity.java`)의 임포트 문은 `androidx.appcompat.widget.Toolbar`를 가리키고 있지만, 레이아웃 XML 파일에서 단순히 `<Toolbar>` 태그를 사용하여 안드로이드 프레임워크 기본 클래스가 인플레이트되었다는 것을 짚어냄.
  - **해결 과정**: AI의 가이드에 따라 XML 파일의 태그를 패키지 경로를 포함한 `<androidx.appcompat.widget.Toolbar>`로 정확히 명시함. 또한 `MainActivity`에서도 발생하던 잠재적인 캐스팅 위험 요소를 함께 제거함.
- **수정 코드 (XML 및 Java 캐스팅)**:
```xml
<!-- AS-IS (에러 발생) -->
<Toolbar android:id="@+id/toolbar" ... />

<!-- TO-BE (해결) -->
<androidx.appcompat.widget.Toolbar 
    android:id="@+id/toolbar"
    app:titleTextColor="@color/white"
    ... />
```
```java
// GroupDetailActivity.java
Toolbar toolbar = findViewById(R.id.toolbar);
setSupportActionBar(toolbar); // 이제 androidx 타입으로 정상 캐스팅됨
```
- **이해 및 성찰**
  - 자바 코드만 잘 짜면 되는 줄 알았는데, XML에서 태그 하나 잘못 쓴 게 앱을 터트릴 수 있다는 걸 뼈저리게 느꼈어요.
  - 에러 로그가 났을 때 단순히 오타만 찾는 게 아니라, 라이브러리 간의 호환성이나 클래스 경로까지 꼼꼼히 확인해야 한다는 좋은 교훈을 얻었습니다.

### 2) 타이머 모드(집중/휴식) 전환 시 데이터 증발 및 누적 오류

- **활용 리소스**: Gemini, Log.d 출력
- **문제 상황**: 사용자가 집중 타이머를 가동하다가 수동으로 '휴식' 버튼을 누르면, 지금까지 집중했던 시간이 오늘 총 집중 시간에 반영되지 않고 사라지는 현상을 발견함. 특히 타이머가 멈추기 직전의 자투리 시간이 기록되지 않았음.
- **초기 Prompt/에러 입력**: _"집중 모드에서 휴식 모드로 버튼을 눌러서 바꾸면, 방금 전까지 공부했던 시간이 오늘 총 공부 시간에 안 합쳐지고 그냥 사라져버려. 타이머 멈추는 로직에 문제가 있는 것 같아."_
- **디버깅 과정**
  - **가설 설정**: 타이머 상태를 바꾸는 `setMode()` 함수가 호출될 때, 현재 진행 중인 세션을 저장하는 로직이 상태 변수 업데이트 후에 실행되어 '이미 바뀐 상태(휴식)'를 기준으로 계산하고 있을 것이라 추측함.
  - **분석**: AI와 함께 코드의 실행 순서를 추적한 결과, `stopTimer()` 호출 전에 남은 시간(`timeLeft`)을 체크하여 차분(`totalSessionTime - timeLeft`)을 먼저 계산해야 함을 확인함.
  - **해결**: `setMode()` 함수의 가장 첫 줄에서 경과 시간을 계산하여 `totalCumulativeMillis`에 즉시 합산하고 `saveLogToFirebase()`를 호출하도록 로직의 순서를 전면 재배치함.
- **수정 코드 (상태 변이 전 데이터 보존 로직)**:
```java
// HomeFragment.java
private void setMode(boolean isFocus) {
    if (isRunning) {
        // [중요] 상태(isFocusMode)를 바꾸기 전에 현재까지의 경과 시간을 먼저 계산하여 누적
        long elapsed = totalSessionTime - timeLeft;
        if (isFocusMode) totalCumulativeMillis += elapsed; 
        
        stopTimer();
        addRecordToTable(); // DB 저장 로직 호출
    }
    // 이후에 모드 전환 및 UI 갱신 수행
    this.isFocusMode = isFocus;
    ...
}
```
- **이해 및 성찰**
  - 코드가 실행되는 '순서' 하나 때문에 공들여 공부한 시간이 사라질 수도 있다는 게 정말 무서웠어요.
  - 상태를 바꿀 때는 아주 세밀하게 로직의 순서를 설계해야 데이터가 안전하게 저장된다는 걸 깊이 깨달았습니다.

### 3) 비동기 Firestore 데이터 로딩과 리사이클러뷰 갱신 타이밍 이슈

- **활용 리소스**: Android Lifecycle 가이드, Gemini
- **문제 상황**: 그룹 목록 화면에 처음 진입했을 때, 분명 DB에는 데이터가 있는데 화면에는 아무것도 뜨지 않다가 화면을 나갔다 들어와야만 목록이 보이는 현상이 간헐적으로 발생함.
- **초기 Prompt/에러 입력**: _"파이어베이스에서 데이터를 분명히 읽어왔는데 리사이클러뷰 목록에 아무것도 안 떠. 화면을 껐다 켜야만 나오는데 실시간으로 바로 업데이트되게 하려면 어떻게 해야 해?"_
- **디버깅 과정**
  - **원인 파악**: AI가 코드를 검토한 후 "비동기 콜백 함수의 실행 완료 시점과 어댑터 세팅 시점의 불일치"를 원인으로 지목함. Firestore에서 데이터를 다 읽어오기도 전에 `onCreateView`가 끝나버리고, 데이터가 도착했을 때 `notifyDataSetChanged()`가 호출되지 않았던 것임.
  - **해결**: 실시간 리스너(`addSnapshotListener`) 내부에서 데이터가 성공적으로 도착했을 때만 `groupList.clear()`와 `addAll()`을 수행하고, 반드시 콜백의 마지막에 `adapter.notifyDataSetChanged()`를 호출하도록 구조를 강제함. 또한 화면이 파괴될 때(`onDestroyView`) 리스너를 명시적으로 해제하여 메모리 누수를 방지함.
- **수정 코드 (실시간 리스너 기반 UI 동기화)**:
```java
// GroupFragment.java
private void setupGroupsListener() {
    groupsListener = repository.getUserGroupsListener(uid, (value, error) -> {
        if (value != null) {
            groupList.clear(); // 1. 기존 리스트 초기화
            for (DocumentSnapshot doc : value.getDocuments()) {
                groupList.add(doc.toObject(Group.class)); // 2. 새 데이터 채우기
            }
            // 3. [핵심] 데이터 변경을 어댑터에 즉시 알려 리스트 갱신
            adapter.notifyDataSetChanged(); 
        }
    });
}
```
- **이해 및 성찰**
  - 데이터는 분명 DB에 들어있는데 화면엔 아무것도 안 뜨는 걸 보면서 정말 멘붕이었어요.
  - 비동기로 데이터를 가져올 때는 안드로이드의 생명주기와 리스트 갱신 타이밍을 맞추는 게 핵심이라는 걸 확실히 알게 된 계기가 되었습니다.

### 4) Firestore 하위 컬렉션 삭제 누락 및 고립된 데이터(Orphaned Data) 문제

- **활용 리소스**: Firebase Console, Gemini CLI, Stack Overflow
- **문제 상황**: 그룹 관리자가 그룹을 삭제했을 때, 최상위 `groups` 문서는 지워지지만 하위의 `members` 컬렉션 데이터가 그대로 남아 DB 용량을 차지하고 보안 규칙을 우회할 수 있는 '고립된 데이터' 문제가 발생함.
- **초기 Prompt/에러 입력**: _"Firestore에서 그룹 문서를 지웠는데, 그 밑에 있는 members 컬렉션 데이터는 안 지워지고 그대로 남아있어. 상위 문서를 지울 때 하위 데이터까지 한 번에 다 지우는 방법 알려줘."_
- **디버깅 과정**
  - **분석**: Firestore는 기본적으로 상위 문서를 지운다고 하위 컬렉션이 자동으로 삭제되지 않는 'NoSQL의 특성'을 가지고 있음을 AI를 통해 재확인함.
  - **해결**: AI와 함께 'Recursive Delete' 패턴을 설계함. 먼저 하위 `members` 컬렉션의 모든 문서를 `get()`으로 가져와 `WriteBatch`에 삭제 명령을 추가한 뒤, 마지막에 상위 그룹 문서를 삭제하도록 구현하여 데이터 무결성을 확보함.
- **핵심 코드 (계층형 데이터 완전 삭제)**:
```java
// FirestoreRepository.java
public Task<Void> deleteGroup(String groupId) {
    DocumentReference groupRef = db.collection("groups").document(groupId);
    // 1. 하위 멤버 리스트를 먼저 조회
    return groupRef.collection("members").get().continueWithTask(task -> {
        WriteBatch batch = db.batch();
        if (task.isSuccessful() && task.getResult() != null) {
            // 2. 모든 멤버 문서 삭제를 배치에 담음
            for (DocumentSnapshot doc : task.getResult()) {
                batch.delete(doc.getReference());
            }
        }
        // 3. 마지막에 그룹 자체를 삭제 (Atomic Operation)
        batch.delete(groupRef);
        return batch.commit();
    });
}
```
- **이해 및 성찰**
  - 그룹만 지우면 다 지워지는 줄 알았는데 멤버 정보가 그대로 남아있는 걸 보고 '유령 데이터' 관리가 얼마나 무서운지 알게 됐어요.
  - 데이터를 지울 때도 그냥 지우는 게 아니라, 연결된 정보들까지 책임지고 정리해야 깔끔한 DB가 유지된다는 걸 배웠습니다.

### 5) 실시간 랭킹의 '데이터 불일치' 및 시각적 지연 해결 (Look-ahead Logic)

- **활용 리소스**: Log.d, Gemini CLI (Plan Mode)
- **문제 상황**: 그룹 상세 화면에서 멤버들의 랭킹을 보여줄 때, 사용자가 타이머를 켜고 공부 중임에도 불구하고 랭킹 리스트의 '오늘 총 집중 시간'은 타이머가 멈추기 전까지 갱신되지 않아 실시간성이 떨어지는 문제가 발생함.
- **초기 Prompt/에러 입력**: _"실시간 DB에 저장된 시간만 보여주니까 공부 중인 사람들의 랭킹이 안 변해. 타이머가 작동 중일 때도 실시간으로 시간이 합산되어서 리스트에 보이게 하려면 어떻게 계산해야 할까?"_
- **디버깅 과정**
  - **가설**: DB(Firestore)에는 세션이 종료될 때만 최종 합산 결과가 반영되기 때문임.
  - **해결**: AI가 "클라이언트 측 Look-ahead(앞서보기) 로직"을 제안함. 실시간 DB(RTDB)에 상태를 전송할 때, 단순히 저장된 과거의 총합만 보내는 것이 아니라 **'현재 활성화된 타이머의 경과 시간'을 실시간으로 더해서** 전송하도록 로직을 수정함.
- **핵심 코드 (실시간 누적 합산 전송)**:
```java
// GroupDetailActivity.java
private void syncStatusToRtdb() {
    long totalTodayFocus = totalCumulativeMillis; // DB에서 가져온 과거 기록
    if (isRunning && isFocusMode) {
        // [핵심] 현재 실시간으로 흐르고 있는 시간(잔여 시간 차분)을 즉석에서 합산
        totalTodayFocus += (totalSessionTime - timeLeft);
    }
    // 합산된 '진짜 실시간' 데이터를 RTDB에 쏴서 다른 유저들에게 공유
    rtdbRepository.updateUserStatus(groupId, currentUserId, ..., totalTodayFocus);
}
```
- **이해 및 성찰**
  - DB에 저장된 값만 그대로 보여주면 사용자가 앱이 '느리다'고 오해할 수 있다는 걸 알았습니다.
  - 실시간으로 변하는 시간은 앱에서 즉석에서 계산해 보여주는 방식을 써보면서, 사용자 입장에서 더 빠르고 쾌적하게 느끼게 만드는 UX 기법의 매력을 느꼈습니다.

## 3. 총평 및 향후 발전 방향

이번 프로젝트를 진행하며 AI는 단순한 코딩 도우미를 넘어, 복잡한 설계를 같이 고민해주는 든든한 **'팀원'** 같다는 느낌을 받았습니다. 특히 파이어베이스의 여러 서비스를 유기적으로 엮으면서 막히는 부분이 정말 많았는데, AI와 대화하며 문제를 하나씩 풀어나가다 보니 안드로이드 아키텍처와 데이터베이스 최적화에 대해 학교 수업보다 훨씬 생생하게 배울 수 있었습니다. 앞으로도 AI를 적극적으로 활용해서 더 완성도 높고 사용자 친화적인 앱을 만들어보고 싶습니다.
