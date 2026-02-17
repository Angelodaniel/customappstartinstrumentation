# Custom App Start Instrumentation with Reduced Timers

This project demonstrates how to implement custom app start instrumentation in Sentry Android SDK with reduced timer threads.

## Implementation Overview

### 1. Sentry Integration

Added Sentry Android SDK (v8.32.0) with the following configuration:

**Files Modified:**
- `gradle/libs.versions.toml` - Added Sentry version, library, and plugin
- `app/build.gradle.kts` - Applied Sentry plugin and added dependency
- `app/src/main/AndroidManifest.xml` - Added custom Application class

### 2. Disabled Automatic App Start Tracking

Automatic app start tracking is disabled in two places:

**In `app/build.gradle.kts`:**
```kotlin
sentry {
    tracingInstrumentation {
        enabled.set(false)
    }
}
```

**In `CustomApp.kt`:**
```kotlin
options.isEnableAppStartTracking = false
```

### 3. Custom App Start Instrumentation with Reduced Timers

**Key Innovation: Using TransactionOptions with null timeouts**

The custom implementation in `CustomApp.kt` creates a transaction with `TransactionOptions` that have:
- `deadlineTimeout = null` - Eliminates the deadline timer thread
- `idleTimeout = null` - Eliminates the idle timer thread

This significantly reduces the number of background threads created during app start.

**Implementation Flow:**

1. **Application.onCreate()** (`CustomApp.kt`)
   - Initializes Sentry with automatic app start tracking disabled
   - Starts custom app start transaction with `TransactionOptions` configured for minimal timers
   - Creates an initialization span to track the app start phase

2. **MainActivity First Composition**
   - Uses `LaunchedEffect(Unit)` to detect when UI is first composed
   - Finishes the initialization span and app start transaction
   - Cleans up the transaction holder

### 4. Files Created/Modified

**New Files:**
- `app/src/main/java/com/example/custom_app_start_instrumentation/CustomApp.kt` - Custom Application class with Sentry initialization and custom app start tracking

**Modified Files:**
- `gradle/libs.versions.toml` - Added Sentry dependencies
- `app/build.gradle.kts` - Applied Sentry plugin, added dependency, disabled automatic instrumentation
- `app/src/main/AndroidManifest.xml` - Added custom Application class reference
- `app/src/main/java/com/example/custom_app_start_instrumentation/MainActivity.kt` - Added transaction finish logic in LaunchedEffect

## Configuration

### Setting Your DSN

Before running the app, update the DSN in `CustomApp.kt`:

```kotlin
options.dsn = "YOUR_DSN_HERE" // Replace with your actual Sentry DSN
```

### Build and Run

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Benefits of This Approach

1. **Reduced Thread Creation**: By setting `deadlineTimeout` and `idleTimeout` to null, we eliminate unnecessary timer threads
2. **Full Control**: You can customize exactly when the app start transaction begins and ends
3. **Custom Spans**: Add custom spans to measure specific initialization phases
4. **Better Performance**: Fewer threads mean less overhead during critical app start time

## Testing

1. Run the app with Sentry debug mode enabled (already configured in `CustomApp.kt`)
2. Check logcat for Sentry transaction events
3. Verify in Sentry dashboard that the custom `app.start.custom` transaction appears
4. Compare thread dumps with/without custom TransactionOptions to see the reduction in timer threads

## Additional Customization

You can further customize the app start tracking:

```kotlin
// Add more spans for different initialization phases
val span1 = transaction.startChild("app.start.network")
// ... network initialization
span1.finish()

val span2 = transaction.startChild("app.start.database")
// ... database initialization
span2.finish()

// Finish transaction when truly ready (e.g., after first API call completes)
```

## Timer Thread Reduction Verification

To verify the reduction in timer threads:

1. **With Default Sentry Configuration**: Standard TransactionOptions create multiple timer threads for deadline and idle timeout management
2. **With Custom Configuration**: Setting both timeouts to null eliminates these timer threads completely

You can verify this using Android Profiler or by examining thread dumps during app start.
