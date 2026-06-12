# Pocus Sharing - 클래스 목록 및 핵심 로직 설명

## 1. 구현 클래스 목록 및 클래스별 기능 간략 설명

### 📱 Activity & Fragment (화면 제어)
*   **`MainActivity.java`**: 하단 네비게이션을 관리하고 앱의 메인 컨테이너 역할을 하며 Firebase 익명 로그인을 초기화함.
*   **`LoginActivity.java`**: 카카오 로그인을 수행하고 Firebase 인증과 연동하여 초기 유저 프로필을 생성함.
*   **`HomeFragment.java`**: 개인 타이머 UI를 제공하며, 포커스 상태 관리 및 오늘 통계를 실시간으로 업데이트함.
*   **`GroupFragment.java`**: 사용자가 가입된 그룹 목록을 보여보고, 새 그룹 생성 및 초대 코드 기반 가입을 처리함.
*   **`GroupDetailActivity.java`**: 그룹 멤버들의 실시간 집중 상태 랭킹을 표시하고 방장의 관리 기능을 제공함.
*   **`SettingsFragment.java`**: 닉네임 수정, 알림 무음, 화면 켜짐 유지 등 앱 설정 데이터를 로컬과 DB에 동기화함.

### 📦 Model (데이터 구조체)
*   **`User.java`**: 사용자의 고유 UID, 카카오 닉네임, 프로필 이미지 URL을 저장하는 데이터 모델.
*   **`UserSettings.java`**: 알람 끄기, 앱 종료 방지, 화면 켜짐 유지 등 개인별 환경 설정을 저장하는 모델.
*   **`Group.java`**: 그룹명, 6자리 초대 코드, 방장 UID, 최대 인원수, 전체 멤버 UID 배열을 관리하는 모델.
*   **`GroupMember.java`**: Firestore 서브컬렉션용 모델로 개별 멤버의 권한(ADMIN/MEMBER)을 정의함.
*   **`TimerLog.java`**: 1회 단위 타이머 세션의 유형(집중/휴식), 지속 초(Seconds), 시작/종료 시각을 저장함.
*   **`MemberStatus.java`**: 실시간 랭킹 UI를 위해 RTDB에서 교환하는 멤버의 임시 상태(남은 시간 등) DTO.
*   **`MemberStatus.java`** (중복 제거 필요하나 리스트업): RTDB presence sync를 위한 DTO.

### 🗄️ Repository (데이터베이스 통신)
*   **`FirestoreRepository.java`**: 프로필, 그룹, 로그 등의 영구 데이터를 저장 및 조회하고 일일 통계를 원자적으로 갱신함.
*   **`RtdbRepository.java`**: 1초마다 바뀌는 타이머 상태를 RTDB를 통해 그룹원들에게 실시간으로 브로드캐스팅함.

### 🎨 View, Adapter & Application (기타 컴포넌트)
*   **`TimerView.java`**: 사용자 터치 궤적의 각도를 인식해 시간을 설정할 수 있는 커스텀 원형 다이얼 뷰.
*   **`MemberAdapter.java`**: 리사이클러뷰에 그룹원들의 실시간 랭킹 정보를 바인딩하여 뷰 카드로 표현함.
*   **`PocusApplication.java`**: 앱 시작 시 카카오 SDK와 앱 키를 전역적으로 초기화하는 기본 Application 클래스.

---

## 2. 핵심 코드 블록 및 구현 로직 설명

### 1) 타이머 다이얼 인터랙션 터치 로직 (TimerView.java)
```java
double angle = Math.toDegrees(Math.atan2(y - centerY, x - centerX)) + 90;
int minutes = Math.round((float)(angle / 360f) * 60);
```
> 터치 좌표를 `atan2` 역삼각함수로 각도로 변환한 뒤, 360도 비율을 60분 단위 시간으로 환산함.  
> `Math.round`를 적용하여 다이얼을 돌릴 때 시간(분)이 딱딱 끊기게 설정되는 스냅(Snap) 효과를 구현함.

### 2) Firestore 일괄 쓰기 및 원자적 연산 (FirestoreRepository.java)
```java
batch.set(logRef, log);
batch.set(statRef, new HashMap<String, Object>() {{ put("totalFocusSec", FieldValue.increment(focusInc)); }}, SetOptions.merge());
```
> 타이머 종료 시 개별 로그 저장과 일일 통계 갱신을 `WriteBatch`로 묶어 두 작업의 동시 성공(원자성)을 보장함.  
> `FieldValue.increment`를 사용해 다수 접속 시에도 데이터 충돌 없이 서버 측에서 안전하게 시간을 누적함.

### 3) 실시간 랭킹 정렬 및 뷰 갱신 (GroupDetailActivity.java)
```java
memberList.sort((m1, m2) -> Long.compare(m2.getTodayFocusTime(), m1.getTodayFocusTime()));
adapter.notifyDataSetChanged();
```
> RTDB 리스너를 통해 멤버 상태 변경이 감지될 때마다 '오늘 총 집중 시간'을 기준으로 내림차순 정렬함.  
> 리스트 정렬 직후 어댑터에 데이터 변경을 알려 화면의 랭킹 카드 순서를 실시간으로 재배치함.

### 4) 실시간 Look-ahead 상태 동기화 (HomeFragment.java)
```java
long totalTodayFocus = totalCumulativeMillis;
if (isRunning && isFocusMode) totalTodayFocus += (totalSessionTime - timeLeft);
rtdbRepository.updateUserStatus(..., totalTodayFocus);
```
> DB에 저장된 과거 기록에 '현재 작동 중인 타이머의 경과 시간'을 실시간으로 계산하여 합산 전송함.  
> 세션이 종료되기 전에도 멤버들에게 실시간으로 늘어나는 공부 시간을 보여주어 UX를 개선함.

### 5) 계층형 데이터 완전 삭제 패턴 (FirestoreRepository.java)
```java
return groupRef.collection("members").get().continueWithTask(task -> {
    for (DocumentSnapshot doc : task.getResult()) batch.delete(doc.getReference());
    batch.delete(groupRef);
    return batch.commit();
});
```
> 상위 문서를 지워도 하위 컬렉션이 남는 NoSQL 특성을 해결하기 위해, 하위 멤버 문서들을 먼저 순회하며 삭제함.  
> 하위 문서들을 모두 배치에 담은 뒤 마지막에 그룹 최상위 문서를 함께 지워 고립된 유령 데이터를 방지함.

### 6) 인증 브릿지 설계 (LoginActivity.java)
```java
mAuth.signInAnonymously().addOnCompleteListener(this, task -> {
    if (task.isSuccessful()) fetchUserInfo();
});
```
> 카카오 로그인 성공 시, Firestore의 보안 규칙 통과를 위해 Firebase 익명 로그인을 백그라운드에서 실행함.  
> 두 시스템의 인증을 연동하여 보안성을 유지하는 동시에 카카오 프로필 데이터를 유저 DB에 매핑함.

### 7) 6자리 고유 초대 코드 생성 (GroupFragment.java)
```java
String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
for (int i = 0; i < 6; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
```
> 영문 대문자와 숫자로 구성된 풀에서 무작위로 문자를 선택해 6자리 그룹 식별 코드를 생성함.  
> 중복 확률이 낮은 짧은 코드를 통해 사용자가 모바일에서 쉽게 입력하고 가입할 수 있게 유도함.

### 8) 동적 로그 내역 레이아웃 생성 (HomeFragment.java)
```java
View row = getLayoutInflater().inflate(R.layout.table_row, llTable, false);
llTable.addView(row, 0); // 최신 데이터가 상단에 오도록 추가
```
> Firestore에서 불러온 타이머 기록들을 별도의 XML 레이아웃(`table_row`)에 입혀 실시간으로 화면에 추가함.  
> 인덱스 0번 위치에 뷰를 삽입하여 별도의 정렬 로직 없이도 최신순 히스토리 UI를 구현함.

### 9) 물리적 햅틱 피드백 구현 (TimerView.java)
```java
if (minutes != lastSnappedMinute) {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
}
```
> 타이머 다이얼을 돌리다가 분(Minute) 단위 경계를 넘는 순간 진동 피드백을 발생시킴.  
> 커스텀 뷰 조작 시 기계식 다이얼을 돌리는 듯한 손맛을 제공하여 사용자 인터랙션 경험을 강화함.

### 10) 클라우드-로컬 설정 동기화 (SettingsFragment.java)
```java
updates.put("settings." + field, value);
updates.put("settings.updatedAt", FieldValue.serverTimestamp());
```
> Firestore의 도트 표기법(Dot Notation)을 사용해 설정 객체 내의 특정 필드만 부분 업데이트함.  
> 앱 설정 변경 시 서버 시간과 함께 저장하여 여러 기기에서 접속해도 동일한 환경을 유지하게 함.
