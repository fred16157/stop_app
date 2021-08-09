package com.stop.stop_app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.github.appintro.AppIntro;
import com.github.appintro.AppIntroFragment;

public class MainAppIntro extends AppIntro {
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addSlide(AppIntroFragment.newInstance("사용 전 주의사항",
                "멈춰! 앱은 카메라와 인공지능, 알림을 활용해 보행자의 안전을 보조하는 역할을 해줍니다.\n그러나 카메라의 오작동이나 인공지능 모델의 잘못된 판단으로 앱이 제 역할을 하지 못할 수 있습니다.\n\n앱에 지나치게 의존하기 보다는 주변 상황에 주의하시는 것을 권장드립니다.",
                R.drawable.outline_warning_24, Color.parseColor("#c50e29")));
        addSlide(AppIntroFragment.newInstance("카메라 권한 요청",
                "멈춰! 앱은 사용자의 실시간 전방 시야를 분석하기 위해 카메라를 사용합니다. 이 이미지 데이터는 인터넷으로 전송되지 않으며, 분석된 데이터는 즉시 폐기됩니다.",
                R.drawable.outline_photo_camera_24, Color.parseColor("#7b1fa2")));
        addSlide(AppIntroFragment.newInstance("시작하기",
                "이제 멈춰! 앱을 사용할 준비가 되었습니다! 오른쪽 아래의 완료 버튼을 터치해주세요.",
                R.drawable.outline_done_24, Color.parseColor("#512da8")));
        setWizardMode(true);
        setColorTransitionsEnabled(true);
        askForPermissions(new String[]{Manifest.permission.CAMERA}, 2, true);
    }

    @Override
    protected void onSkipPressed(@org.jetbrains.annotations.Nullable Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        finish();
    }

    @Override
    protected void onDonePressed(@org.jetbrains.annotations.Nullable Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putBoolean("isFirstLaunch", false).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
