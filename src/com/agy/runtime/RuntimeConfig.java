package com.agy.runtime;

import android.content.Context;

import com.agy.BuildConfig;

/** App-level constants sourced from the generated BuildConfig. */
public final class RuntimeConfig {

    public static final int DEFAULT_HTTPS_PORT = BuildConfig.PORT;
    public static final String CORE_VERSION = BuildConfig.CORE_VERSION;
    public static final String APP_VERSION_NAME = BuildConfig.APP_VERSION_NAME;
    public static final int APP_VERSION_CODE = BuildConfig.APP_VERSION_CODE;
    public static final int MIN_API = BuildConfig.MIN_API;
    public static final int TARGET_SDK = BuildConfig.TARGET_SDK;
    public static final boolean DEBUG = BuildConfig.DEBUG;

    private RuntimeConfig() {
    }

    /** The port from res/values/gen.xml, else the BuildConfig default. */
    public static int httpsPort(Context appContext) {
        return DEFAULT_HTTPS_PORT;
    }
}
