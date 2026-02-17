package com.example.custom_app_start_instrumentation

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.MeasurementUnit
import io.sentry.SpanStatus
import io.sentry.TransactionOptions
import java.io.File

class CustomApp : Application() {

    override fun onCreate() {
        // Capture app start timestamp as early as possible
        val appStartTimeMs = SystemClock.uptimeMillis()

        super.onCreate()

        // Initialize Sentry with custom configuration
        SentryAndroid.init(this) { options ->
            options.dsn = "https://c8b74f6357b7a7511124d3c3a580f2b0@o4508065179762768.ingest.de.sentry.io/4510901291778128"
            options.isDebug = true
            options.tracesSampleRate = 1.0

            // Disable automatic app start tracking - we'll do it manually
            options.isEnableAutoActivityLifecycleTracing = false
            options.profilesSampleRate = null
        }

        // Start custom app start instrumentation with reduced timers
        AppStartTracker.startAppStartTracking(this, appStartTimeMs)
    }
}

/**
 * Custom App Start Tracker that replicates Sentry's automatic app start measurement
 * but with reduced timer threads by using TransactionOptions with null timeouts.
 *
 * Follows Sentry's app start specification:
 * https://develop.sentry.dev/sdk/telemetry/traces/modules/app-starts/
 */
object AppStartTracker {
    private var appStartTransaction: ITransaction? = null
    private var appStartTimeMs: Long = 0
    private var processStartTimeMs: Long = 0
    private var isColdStart: Boolean = true

    // Spans for different phases
    private var processInitSpan: ISpan? = null
    private var appStartSpan: ISpan? = null
    private var activityCreationSpan: ISpan? = null
    private var firstFrameSpan: ISpan? = null

    fun startAppStartTracking(application: Application, appStartTime: Long) {
        appStartTimeMs = appStartTime
        processStartTimeMs = getProcessStartTimeMs()

        // Determine if this is a cold start
        // Cold start = process creation, Warm start = activity creation with existing process
        isColdStart = true // First launch is always cold

        // Check if there's already an active transaction
        val existingTransaction = Sentry.getSpan()
        if (existingTransaction != null) {
            // If there's already a transaction, add app start as a child span instead
            android.util.Log.d("AppStartTracker", "Found existing transaction, attaching as child span")
            val startType = if (isColdStart) "cold" else "warm"

            // Add app start timing as a child span of existing transaction
            appStartSpan = existingTransaction.startChild(
                "app.start.$startType",
                "App Start ($startType)"
            )?.apply {
                setData("app_start_type", startType)
                setTag("start_type", startType)

                // Add app start measurement to the existing transaction
                existingTransaction.setMeasurement("app_start_cold",
                    (SystemClock.uptimeMillis() - processStartTimeMs).toDouble(),
                    MeasurementUnit.Duration.MILLISECOND)
            }
            return
        }

        // Create transaction with reduced timers
        val transactionOptions = TransactionOptions().apply {
            deadlineTimeout = null  // No deadline timer = reduced threads
            idleTimeout = null      // No idle timer = reduced threads
            isWaitForChildren = true
            isTrimEnd = true
        }

        val startType = if (isColdStart) "cold" else "warm"

        // Use operation "ui.load" so it appears in Mobile Vitals
        // Transaction name will be set to the activity name later
        appStartTransaction = Sentry.startTransaction(
            "MainActivity",  // Activity name for Mobile Vitals
            "ui.load",       // Operation recognized by Mobile Vitals
            transactionOptions
        ).apply {
            setData("app_start_type", startType)
            setTag("start_type", startType)
            setTag("ui.load.type", startType)  // Additional tag for Mobile Vitals

            // Set the app start cold/warm measurement that Mobile Vitals expects
            if (isColdStart) {
                setMeasurement("app_start_cold", 0.0, MeasurementUnit.Duration.MILLISECOND)  // Will update at finish
            } else {
                setMeasurement("app_start_warm", 0.0, MeasurementUnit.Duration.MILLISECOND)  // Will update at finish
            }

            // Store timing information as metadata
            val elapsedSinceProcessStart = appStartTimeMs - processStartTimeMs
            setData("process_init_duration_ms", elapsedSinceProcessStart)
            setData("process_start_time_ms", processStartTimeMs)
        }

        // Span 1: Process Initialization (from process start to Application.onCreate)
        // Note: We create spans immediately as we track the phases
        if (isColdStart) {
            processInitSpan = appStartTransaction?.startChild(
                "app.start.process.init"
            )?.apply {
                description = "Process Initialization"
                // Record the actual duration as data since we can't set historical timestamps
                val duration = appStartTimeMs - processStartTimeMs
                setData("actual_duration_ms", duration)
            }
        }

        // Span 2: Application.onCreate (currently in progress)
        appStartSpan = appStartTransaction?.startChild(
            "app.start.application.create"
        )?.apply {
            description = "Application.onCreate"
        }

        // Finish process init span (it ended when Application.onCreate started)
        processInitSpan?.finish()

        // Register activity lifecycle callbacks to measure activity creation and first frame
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var activityCreated = false

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!activityCreated) {
                    activityCreated = true

                    // Update transaction name to actual activity name for Mobile Vitals
                    appStartTransaction?.name = activity.javaClass.simpleName

                    // Finish app.start.application.create span
                    appStartSpan?.finish()

                    // Start activity creation span
                    activityCreationSpan = appStartTransaction?.startChild(
                        "app.start.activity.create"
                    )?.apply {
                        description = "${activity.javaClass.simpleName}.onCreate"
                        setData("activity", activity.javaClass.simpleName)
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                if (activityCreationSpan != null) {
                    // Finish activity creation span
                    activityCreationSpan?.finish()
                    activityCreationSpan = null

                    // Start first frame span - measures until first draw
                    firstFrameSpan = appStartTransaction?.startChild(
                        "app.start.first.frame"
                    )?.apply {
                        description = "First Frame Render"
                    }

                    // Wait for first frame using Choreographer
                    Choreographer.getInstance().postFrameCallback {
                        firstFrameSpan?.finish(SpanStatus.OK)

                        // Calculate total duration
                        val totalDurationMs = SystemClock.uptimeMillis() - processStartTimeMs
                        appStartTransaction?.setData("duration_ms", totalDurationMs)

                        // Update the app start measurement with actual duration for Mobile Vitals
                        val measurementName = if (isColdStart) "app_start_cold" else "app_start_warm"
                        appStartTransaction?.setMeasurement(
                            measurementName,
                            totalDurationMs.toDouble(),
                            MeasurementUnit.Duration.MILLISECOND
                        )

                        // Also set time to initial display (TTID) and time to full display (TTFD)
                        // These are recognized by Mobile Vitals
                        appStartTransaction?.setMeasurement(
                            "time_to_initial_display",
                            totalDurationMs.toDouble(),
                            MeasurementUnit.Duration.MILLISECOND
                        )

                        appStartTransaction?.setMeasurement(
                            "time_to_full_display",
                            totalDurationMs.toDouble(),
                            MeasurementUnit.Duration.MILLISECOND
                        )

                        // Finish the app start transaction
                        appStartTransaction?.finish(SpanStatus.OK)

                        // Clean up
                        appStartTransaction = null

                        // Unregister after first activity is fully started
                        application.unregisterActivityLifecycleCallbacks(this)
                    }
                }
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Gets the process start time in milliseconds.
     * Uses different methods depending on API level.
     */
    private fun getProcessStartTimeMs(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+: Use Process.getStartElapsedRealtime()
            val elapsedRealtime = Process.getStartElapsedRealtime()
            val currentTime = SystemClock.elapsedRealtime()
            val currentUptimeMs = SystemClock.uptimeMillis()
            currentUptimeMs - (currentTime - elapsedRealtime)
        } else {
            // Fallback: Read from /proc/self/stat
            try {
                val stat = File("/proc/self/stat").readText()
                val startTimeJiffies = stat.split(")")[1].trim().split(" ")[19].toLong()
                val clockTicksPerSecond = 100L // Standard on most systems
                val startTimeMs = (startTimeJiffies * 1000) / clockTicksPerSecond
                val uptimeMs = SystemClock.uptimeMillis()
                val bootTimeMs = System.currentTimeMillis() - uptimeMs
                startTimeMs - bootTimeMs
            } catch (e: Exception) {
                // Fallback: Use current uptime as approximation
                SystemClock.uptimeMillis()
            }
        }
    }
}
