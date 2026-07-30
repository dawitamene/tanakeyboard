# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

-keep,allowoptimization class com.google.firebase.FirebaseApp {
    public static final java.lang.String DEFAULT_APP_NAME;
    public static java.util.List getApps(android.content.Context);
    public static com.google.firebase.FirebaseApp initializeApp(android.content.Context);
    public java.lang.String getName();
}

-keep,allowoptimization class com.google.firebase.analytics.FirebaseAnalytics {
    public static com.google.firebase.analytics.FirebaseAnalytics getInstance(android.content.Context);
    public void logEvent(java.lang.String, android.os.Bundle);
    public void resetAnalyticsData();
    public void setAnalyticsCollectionEnabled(boolean);
    public void setConsent(java.util.Map);
}

-keep,allowoptimization enum com.google.firebase.analytics.FirebaseAnalytics$ConsentType {
    *;
}

-keep,allowoptimization enum com.google.firebase.analytics.FirebaseAnalytics$ConsentStatus {
    *;
}

-keep,allowoptimization class com.google.firebase.crashlytics.FirebaseCrashlytics {
    public static com.google.firebase.crashlytics.FirebaseCrashlytics getInstance();
    public void deleteUnsentReports();
    public void recordException(java.lang.Throwable);
    public void setCrashlyticsCollectionEnabled(boolean);
    public void setCustomKey(java.lang.String, java.lang.String);
}

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
