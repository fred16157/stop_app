package com.example.stop_app;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

public class IntEditTextPreference extends EditTextPreference {

    public IntEditTextPreference(Context context) {
        super(context);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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