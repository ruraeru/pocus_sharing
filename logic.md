# Pocus Sharing - 클래스 목록 및 핵심 로직 설명

## 1. 구현 클래스 목록 및 기능 요약

### 📱 Activity & Fragment (화면)
*   **`MainActivity.java`**: 앱의 메인 컨테이너. 하단 네비게이션을 관리하고 익명 로그인(Firebase)을 초기화함.
*   **`LoginActivity.java`**: 카카오 로그인을 수행하고 Firestore에 초기 유저 프로필(닉네임, 사진)을 동기화함.
*   **`HomeFragment.java`**: 개인 타이머 기능 제공. 오늘의 집중/휴식 기록을 관리하고 실시간 상태를 DB에 전송함.
*   **`GroupFragment.java`**: 소속된 그룹 목록을 표시함. 6자리 코드를 통한 가입 및 새 그룹 생성 기능을 지원함.
*   **`GroupDetailActivity.java`**: 그룹 내 멤버들의 실시간 타이머 랭킹을 보여주며, 방장의 관리(추방, 수정) 기능을 제공함.
*   **`SettingsFragment.java`**: 사용자 닉네임 수정 및 알람, 화면 유지 등의 로컬/리모트 환경 설정을 관리함.

### 📦 Model (데이터 구조체)
*   **`User.java`**: 사용자 기본 정보(UID, 닉네임)와 앱 설정(Settings)을 담는 모델.
*   **`Group.java`**: 그룹 메타데이터(이름, 코드, 방장 UID)와 가입된 멤버 리스트를 관리함.
*   **`TimerLog.java`**: 1회 단위의 타이머 세션 기록(포커스/휴식, 지속 시간)을 정의함.
*   **`MemberStatus.java`**: 실시간 랭킹을 위해 RTDB에서 교환하는 멤버의 임시 상태(남은 시간, 현재 모드) 객체.
*   **`GroupMember.java`**: 서브컬렉션용 모델로, 특정 그룹 내 멤버의 권한(ADMIN/MEMBER)을 정의함.

### 🗄️ Repository (데이터베이스 통신)
*   **`FirestoreRepository.java`**: 영구 데이터(프로필, 로그, 그룹) 저장 및 일일 통계 합산을 처리함.
*   **`RtdbRepository.java`**: 1초마다 바뀌는 타이머 상태를 그룹원들과 실시간으로 주고받는 통신을 담당함.

### 🎨 View & Adapter (UI 컴포넌트)
*   **`TimerView.java`**: 사용자가 터치로 시간을 설정할 수 있는 커스텀 원형 타이머 뷰.
*   **`MemberAdapter.java`**: 리사이클러뷰를 이용해 그룹 멤버들의 실시간 상태를 카드 형태로 바인딩함.

---

## 2. 핵심 코드 블록 및 로직 설명

### 1) 타이머 다이얼 인터랙션 (TimerView.java)
```java
double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX)) + 90;
float rawProgress = (float) (angle / 360f);
int minutes = Math.round(rawProgress * 60);
```
> 터치 좌표(x, y)를 삼각함수(`atan2`)를 이용해 중심점 기준 각도로 변환함.  
> 이를 0~1 사이의 진행률로 바꾸고, 분(Minute) 단위로 딱딱 끊어지게 스냅(Snap) 처리를 함.

### 2) Firestore 일괄 쓰기 및 원자적 연산 (FirestoreRepository.java)
```java
batch.set(userStatRef, new HashMap<String, Object>() {{
    put("totalFocusSec", FieldValue.increment(focusInc));
}}, SetOptions.merge());
batch.commit();
```
> 타이머 종료 시 새 로그 저장과 누적 통계 업데이트를 묶어서 한 번에(`batch`) 처리함.  
> `FieldValue.increment`를 써서 동시 접속 시에도 데이터 충돌 없이 안전하게 시간을 더함.

### 3) 실시간 상태 동기화 및 랭킹 정렬 (GroupDetailActivity.java)
```java
memberList.sort((m1, m2) ->
    Long.compare(m2.getTodayFocusTime(), m1.getTodayFocusTime()));
adapter.notifyDataSetChanged();
```
> RTDB에서 멤버들의 실시간 데이터가 수신되면, '오늘 총 집중 시간'을 기준으로 내림차순 정렬함.  
> 정렬 직후 어댑터에 갱신을 알려 실시간 랭킹 UI를 즉각적으로 재배치함.

### 4) 하이브리드 로그인 브릿지 (LoginActivity.java)
```java
mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
    if (task.isSuccessful()) fetchUserInfo();
});
```
> 카카오 로그인 성공 직후, Firestore의 보안 규칙 통과를 위해 Firebase 익명 로그인을 백그라운드에서 실행함.  
> 두 시스템의 인증을 연동하여 보안성을 유지하며 유저 프로필을 저장함.

### 5) 그룹 데이터 동시성 보장 (FirestoreRepository.java)
```java
batch.set(groupRef.collection("members").document(userId), member);
batch.update(groupRef, "memberIds", FieldValue.arrayUnion(userId));
```
> 유저가 코드를 입력해 가입할 때, 하위 컬렉션에 멤버 객체를 넣고 부모 문서 배열에 UID를 추가함.  
> `arrayUnion`을 써서 중복 가입을 방지하고 여러 명의 동시 가입 요청을 안전하게 처리함.
