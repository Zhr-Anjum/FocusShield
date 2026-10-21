# FocusShield Android App — Build, Google Play Billing & Bank Payout Guide

This guide walks you through compiling **FocusShield** (`FocusShieldApp.kt`) in Android Studio, integrating **Google Play In-App Billing** for your next-to-clock Tip Jar, and setting up your **Google Play Console Merchant Profile** so all user tips arrive as a single, official monthly bank transfer from Google.

## 1. Create the Project in Android Studio

1. Open **Android Studio** and select **New Project** → **Empty Activity (Jetpack Compose)**.
2. Set the project details:
   - **Name:** `FocusShield`
   - **Package name:** `com.focusshield.app`
   - **Minimum SDK:** `API 26 (Android 8.0)`
   - **Target SDK:** `API 34+` (required for new Play Store uploads)
   - **Build configuration language:** `Kotlin DSL (build.gradle.kts)`
3. In `app/build.gradle.kts`, add the official Google Play Billing dependency inside the `dependencies { ... }` block:

```kotlin
dependencies {
    // Official Google Play In-App Billing Library (Kotlin Extensions)
    implementation("com.android.billingclient:billing-ktx:7.1.1")
}
```

4. Replace the generated `MainActivity.kt` inside `app/src/main/java/com/focusshield/app/` with `FocusShieldApp.kt`.

## 2. Create the Accessibility Service Config XML

Create the directory `app/src/main/res/xml/` if it does not exist, and create `app/src/main/res/xml/accessibility_service_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canPerformGestures="false"
    android:canRetrieveWindowContent="true"
    android:description="@string/app_name"
    android:notificationTimeout="150" />
```

## 3. Configure `AndroidManifest.xml`

Replace `app/src/main/AndroidManifest.xml` with the following configuration:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Google Play Billing permission (automatically merged by billing-ktx, declared for clarity) -->
    <uses-permission android:name="com.android.vending.BILLING" />

    <!-- Allows listing all installed apps (Social Media, Trading apps, Games, etc.) -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application
        android:allowBackup="true"
        android:label="FocusShield"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <!-- Main Dashboard Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.action.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Full-Screen Buffer Clock & Next-to-Clock Tip Jar Activity -->
        <activity
            android:name=".LockInterceptorActivity"
            android:excludeFromRecents="true"
            android:exported="false"
            android:launchMode="singleInstance"
            android:taskAffinity="" />

        <!-- Background App & In-App Reels/Shorts Blocker Service -->
        <service
            android:name=".FocusShieldAccessibilityService"
            android:exported="true"
            android:label="FocusShield App &amp; Trading Guard"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
```

## 4. Set Up Google Play Console & Receive Monthly Bank Payouts in India

1. **Register Your Individual Developer Account ($25 / ~₹2,100 One-Time):**
   - Go to `play.google.com/console` and sign up for an **Individual Account** using a Visa/Mastercard debit or credit card with international transactions enabled.
   - Complete identity verification using your **PAN Card, Passport, Aadhaar, or Driving Licence** (make sure the name entered matches your ID character for character).
2. **Link Your Indian Bank Account & Payments Profile:**
   - In Google Play Console, go to **Settings → Payments profile → Create payments profile**.
   - Enter your **PAN Card number** (and GSTIN only if your annual turnover exceeds the ₹20 Lakh threshold or required for interstate digital sales).
   - Under **Payment methods → Add bank account**, enter your **Account Holder Name**, **Bank Account Number**, **IFSC Code**, and your bank branch's **SWIFT / BIC Code** (used by Google to route international/consolidated payouts and generate your official **FIRC — Foreign Inward Remittance Certificate** through BillDesk/Citibank).
   - Enable the **15% Service Fee Tier** under **Associated Developer Accounts** so Google only deducts 15% instead of 30% on your first $1M/year.
3. **Create Your 4 One-Time Support Products (In-App Products):**
   - In Play Console, open your app → **Monetize with Play → Products → In-app products → Create product**.
   - Create these four product IDs (matching `PlayBillingProductIds` in `FocusShieldApp.kt`):
     - `support_tier_chai` — Default Price: **₹49** (Google auto-converts to ~$0.99 / €0.99 worldwide)
     - `support_tier_coffee` — Default Price: **₹99** (Google auto-converts to ~$1.99 / €1.99 worldwide)
     - `support_tier_boost` — Default Price: **₹199** (Google auto-converts to ~$4.99 / €4.99 worldwide)
     - `support_tier_champion` — Default Price: **₹499** (Google auto-converts to ~$9.99 / €9.99 worldwide)
4. **How the Money Reaches Your Bank Account:**
   - Whenever any user in India (via UPI/GPay/PhonePe/Card) or abroad (in USD/EUR/GBP) taps a support tier next to the Buffer Clock, **they pay Google Play directly**—their personal bank account never touches yours.
   - On the **15th of every month**, Google bundles all your previous month's earnings into **one single official bank transfer from Google** straight to your linked Indian bank account, along with downloadable **Monthly Sales & Payout Tax Reports** for clean Income Tax (ITR) filing.
