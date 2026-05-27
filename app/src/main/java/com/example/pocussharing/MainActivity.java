package com.example.pocussharing;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.Executor;

/**
 * MainActivity: 앱의 주요 화면을 관리하고 네비게이션을 담당하는 메인 액티비티
 */
public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 시스템 바 영역까지 화면을 확장하는 EdgeToEdge 활성화
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Firebase 인증 초기화 및 사용자 상태 확인
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            signInAnonymously();
        }
        
        // 하단 네비게이션 및 프래그먼트 전환 설정
        setupNavigation();

        // 시스템 바(상태표시줄, 네비게이션 바) 영역에 따른 패딩 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * Navigation Component를 사용하여 하단 네비게이션 바와 프래그먼트 호스트를 연결합니다.
     */
    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
            NavigationUI.setupWithNavController(bottomNav, navController);
        }
    }

    /**
     * 사용자가 인증되지 않은 경우 익명으로 Firebase에 로그인합니다.
     */
    private void signInAnonymously() {
        mAuth.signInAnonymously()
            .addOnCompleteListener((Executor) this, task -> {
                if (task.isSuccessful()) {
                    Log.d("MainActivity", "Firebase 익명 로그인 성공");
                } else {
                    Log.w("MainActivity", "Firebase 익명 로그인 실패", task.getException());
                }
            });
    }
}