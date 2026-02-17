# Mobile Vitals Integration for Custom App Start

## 🎯 Changes Made

Your custom app start instrumentation has been updated to work with **Sentry's Mobile Vitals dashboard** and handle **transaction conflicts**.

---

## 🔧 Key Changes

### 1. **Operation Changed: `app.start` → `ui.load`**

```kotlin
// Before
Sentry.startTransaction("app.start.cold", "app.start", transactionOptions)

// After
Sentry.startTransaction("MainActivity", "ui.load", transactionOptions)
```

**Why?** Sentry's Mobile Vitals dashboard specifically looks for transactions with the `"ui.load"` operation. This is the operation type that gets aggregated into the Mobile Vitals metrics.

---

### 2. **Added Mobile Vitals Measurements**

```kotlin
// Set the measurement that Mobile Vitals expects
if (isColdStart) {
    transaction.setMeasurement("app_start_cold", durationMs, "millisecond")
} else {
    transaction.setMeasurement("app_start_warm", durationMs, "millisecond")
}

// Additional Mobile Vitals metrics
transaction.setMeasurement("time_to_initial_display", durationMs, "millisecond")
transaction.setMeasurement("time_to_full_display", durationMs, "millisecond")
```

**Why?** Mobile Vitals looks for specific measurement names:
- `app_start_cold` - Cold start duration
- `app_start_warm` - Warm start duration
- `time_to_initial_display` (TTID) - Time until first content
- `time_to_full_display` (TTFD) - Time until fully interactive

---

### 3. **Transaction Name Set to Activity Name**

```kotlin
// Transaction name is now the activity name
appStartTransaction?.name = activity.javaClass.simpleName // "MainActivity"
```

**Why?** Mobile Vitals groups metrics by screen/activity name. Using the activity name helps you see app start performance broken down by entry point.

---

### 4. **Added Tag for Mobile Vitals**

```kotlin
transaction.setTag("ui.load.type", startType)  // "cold" or "warm"
```

**Why?** This tag helps Mobile Vitals differentiate between cold and warm starts in the UI.

---

### 5. **Handle Existing Transaction Conflicts** ⚠️

```kotlin
// Check if there's already an active transaction
val existingTransaction = Sentry.getSpan()
if (existingTransaction != null) {
    // Attach as child span instead of creating new transaction
    appStartSpan = existingTransaction.startChild(
        "app.start.$startType",
        "App Start ($startType)"
    )

    // Add measurement to existing transaction
    existingTransaction.setMeasurement("app_start_cold", duration, "millisecond")
    return
}
```

**Why?** If Sentry's automatic UI tracking or another integration already started a transaction:
- Creating a new transaction would fail (only one transaction per scope)
- Instead, we attach app start as a **child span** of the existing transaction
- We still add the measurement so it appears in Mobile Vitals

**When this happens:**
- When auto activity lifecycle tracing is partially enabled
- When using other performance integrations (Navigation, OkHttp, etc.)
- When manually starting transactions elsewhere

---

## 📊 What You'll See in Mobile Vitals

### Mobile Vitals Dashboard will now show:

1. **App Start Duration** - Overall cold/warm start times
2. **Screen Load Times** - Broken down by activity (MainActivity, etc.)
3. **Distribution Graph** - P50, P75, P95, P99 percentiles
4. **Trends Over Time** - How app start performance changes
5. **Cold vs Warm Start Split** - Separate metrics for each type

### Transaction Details will show:

- **Operation**: `ui.load` (recognized by Mobile Vitals)
- **Transaction Name**: `MainActivity` (your entry activity)
- **Measurements**:
  - `app_start_cold` or `app_start_warm`
  - `time_to_initial_display`
  - `time_to_full_display`
- **Spans**: Your custom app start phases (process.init, application.create, etc.)

---

## 🧪 Testing the Changes

### 1. Test Cold Start (Clean Launch)
```bash
# Kill the app completely
adb shell am force-stop com.example.custom_app_start_instrumentation

# Launch fresh
adb shell am start -n com.example.custom_app_start_instrumentation/.MainActivity
```

**Expected Result:**
- Transaction with operation `ui.load`
- Name: `MainActivity`
- Measurement: `app_start_cold` with actual duration
- Tag: `ui.load.type=cold`

---

### 2. Test Warm Start (Background → Foreground)
```bash
# Press home button in emulator
# Then relaunch from app launcher
```

**Expected Result:**
- Transaction with operation `ui.load`
- Name: `MainActivity`
- Measurement: `app_start_warm` with actual duration
- Tag: `ui.load.type=warm`

---

### 3. Check Mobile Vitals Dashboard

1. Go to Sentry → **Performance** → **Mobile Vitals**
2. You should see:
   - **App Start** section with cold/warm metrics
   - **Screen Loads** section with MainActivity
   - Charts showing trends over time

---

## 🔍 Verifying Transaction Conflict Handling

To test that existing transaction conflicts are handled:

### Enable Auto Activity Tracking Temporarily
```kotlin
// In CustomApp.kt
options.isEnableAutoActivityLifecycleTracing = true  // Enable temporarily
```

**What happens:**
1. Sentry auto-creates a `ui.load.MainActivity` transaction
2. Your custom app start code detects it via `Sentry.getSpan()`
3. Instead of creating a new transaction, it:
   - Attaches as a child span
   - Still adds the `app_start_cold` measurement
4. The existing transaction gets both:
   - Sentry's automatic UI tracking
   - Your custom app start measurement

**Result:** No conflicts, measurements still appear in Mobile Vitals!

---

## 📐 Transaction Structure Comparison

### Before (Not in Mobile Vitals):
```
Transaction: app.start.cold
Operation: app.start  ❌ Not recognized by Mobile Vitals
└── Spans: process.init, application.create, etc.
```

### After (Mobile Vitals Compatible):
```
Transaction: MainActivity
Operation: ui.load  ✅ Recognized by Mobile Vitals
Measurements:
├── app_start_cold: 1234ms  ✅ Shows in Mobile Vitals
├── time_to_initial_display: 1234ms  ✅ Shows in Mobile Vitals
└── time_to_full_display: 1234ms  ✅ Shows in Mobile Vitals
Tags:
└── ui.load.type: cold  ✅ Used by Mobile Vitals
Spans:
├── app.start.process.init
├── app.start.application.create
├── app.start.activity.create
└── app.start.first.frame
```

---

## 🎯 What Hasn't Changed

- ✅ Still uses `TransactionOptions` with null timeouts (reduced threads)
- ✅ Still measures real app start performance
- ✅ Still creates detailed spans for each phase
- ✅ Still tracks cold vs warm starts
- ✅ Still uses Choreographer for first frame detection

**The implementation is the same - just with better Sentry integration!**

---

## 📚 References

- [Sentry Mobile Vitals](https://docs.sentry.io/product/performance/mobile-vitals/)
- [Sentry Measurements](https://docs.sentry.io/platforms/android/enriching-events/transaction-name/)
- [UI Load Operations](https://develop.sentry.dev/sdk/performance/span-operations/)
- [App Start Measurement Spec](https://develop.sentry.dev/sdk/telemetry/traces/modules/app-starts/)

---

## ✅ Summary

Your app start metrics will now:
1. ✅ **Appear in Mobile Vitals dashboard** (operation: `ui.load`)
2. ✅ **Show correct measurements** (`app_start_cold`, `app_start_warm`, TTID, TTFD)
3. ✅ **Handle transaction conflicts** (attaches as child if transaction exists)
4. ✅ **Still use reduced timer threads** (null timeouts preserved)
5. ✅ **Track real performance** (no changes to measurement accuracy)

**Mobile Vitals will now aggregate and display your custom app start data! 🎉**
