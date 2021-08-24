package com.stop.stop_app;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;

import org.jetbrains.annotations.NotNull;

public class IntEditTextPreference extends EditTextPreference {

    public IntEditTextPreference(Context context) {
        super(context);
        initInputType();
    }

    public IntEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initInputType();
    }

    public IntEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initInputType();
    }

    public void initInputType() {
        setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        });
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        try {
            return String.valueOf(getPersistedInt(Integer.parseInt(defaultReturnValue)));
        } catch (NumberFormatException e) {
            return String.valueOf(getPersistedInt(0));
        }

    }

    @Override
    protected boolean persistString(String value) {
        return persistInt(Integer.parseInt(value));
    }
}