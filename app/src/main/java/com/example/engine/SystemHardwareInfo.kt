package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemHardwareInfo {

    fun getSystemInfo(context: Context): List<String> {
        val lines = mutableListOf<String>()
        lines.add("================================================================================")
        lines.add("                         WINDOWS / ANDROID SUBSYSTEM INFO                       ")
        lines.add("================================================================================")
        lines.add(String.format("%-26s: %s", "Host Name", "${Build.DEVICE.uppercase()}-PHONE"))
        lines.add(String.format("%-26s: %s", "OS Name", "Android OS (Windows CMD Subsystem)"))
        lines.add(String.format("%-26s: %s (API %d)", "OS Version", Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
        lines.add(String.format("%-26s: %s", "OS Manufacturer", Build.MANUFACTURER.replaceFirstChar { it.uppercase() }))
        lines.add(String.format("%-26s: %s", "System Model", Build.MODEL))
        lines.add(String.format("%-26s: %s", "System Type", Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64-based PC/Phone"))
        lines.add(String.format("%-26s: %s", "Hardware / Board", Build.HARDWARE))
        lines.add(String.format("%-26s: %s", "Build Fingerprint", Build.DISPLAY))
        lines.add(String.format("%-26s: %s", "Bootloader", Build.BOOTLOADER))

        val uptimeMs = SystemClock.elapsedRealtime()
        val hours = uptimeMs / (1000 * 60 * 60)
        val minutes = (uptimeMs % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (uptimeMs % (1000 * 60)) / 1000
        lines.add(String.format("%-26s: %d hours, %d min, %d sec", "System Boot Up Time", hours, minutes, seconds))

        // RAM Info
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val usedRamMb = totalRamMb - availRamMb
        lines.add(String.format("%-26s: %d MB", "Total Physical Memory", totalRamMb))
        lines.add(String.format("%-26s: %d MB", "Available Physical Memory", availRamMb))
        lines.add(String.format("%-26s: %d MB (%d%%)", "Used Physical Memory", usedRamMb, if (totalRamMb > 0) (usedRamMb * 100 / totalRamMb) else 0))

        // JVM Heap
        val runtime = Runtime.getRuntime()
        val jvmMax = runtime.maxMemory() / (1024 * 1024)
        val jvmTotal = runtime.totalMemory() / (1024 * 1024)
        val jvmFree = runtime.freeMemory() / (1024 * 1024)
        lines.add(String.format("%-26s: %d MB (Used: %d MB, Max: %d MB)", "Virtual Memory (JVM)", jvmTotal, jvmTotal - jvmFree, jvmMax))

        // Storage
        val storageStats = getStorageSummary()
        lines.add(String.format("%-26s: %s", "Primary Drive (C:)", storageStats))

        // Battery
        val batteryInfo = getBatterySummary(context)
        lines.add(String.format("%-26s: %s", "Power Status", batteryInfo))

        lines.add("================================================================================")
        return lines
    }

    fun getMemoryDetail(context: Context): List<String> {
        val lines = mutableListOf<String>()
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val freeMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - freeMb

        lines.add("              total        used        free      percent")
        lines.add(String.format("Physical:   %6dMB    %6dMB    %6dMB         %2d%%",
            totalMb, usedMb, freeMb, if (totalMb > 0) (usedMb * 100 / totalMb) else 0))

        val runtime = Runtime.getRuntime()
        val jvmMaxMb = runtime.maxMemory() / (1024 * 1024)
        val jvmAllocatedMb = runtime.totalMemory() / (1024 * 1024)
        val jvmFreeMb = runtime.freeMemory() / (1024 * 1024)
        val jvmUsedMb = jvmAllocatedMb - jvmFreeMb

        lines.add(String.format("JVM Heap:   %6dMB    %6dMB    %6dMB   Max: %dMB",
            jvmAllocatedMb, jvmUsedMb, jvmFreeMb, jvmMaxMb))
        return lines
    }

    fun getDiskSummary(): List<String> {
        val lines = mutableListOf<String>()
        lines.add("Filesystem            Total        Used       Avail   Use%  Mounted on")
        
        val primaryDir = Environment.getExternalStorageDirectory()
        try {
            val stat = StatFs(primaryDir.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            val usedBytes = totalBytes - freeBytes

            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val pct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes) else 0

            lines.add(String.format(Locale.US, "/dev/block/sdcard0  %6.1f GB   %6.1f GB   %6.1f GB   %3d%%  /storage/emulated/0 (C:)",
                totalGb, usedGb, freeGb, pct))
        } catch (_: Exception) {
            lines.add("Internal Storage: Available")
        }

        val rootDir = File("/")
        try {
            val stat = StatFs(rootDir.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            val usedBytes = totalBytes - freeBytes

            val totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
            val usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0)
            val freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            val pct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes) else 0

            lines.add(String.format(Locale.US, "/dev/root           %6.1f GB   %6.1f GB   %6.1f GB   %3d%%  /system (X:)",
                totalGb, usedGb, freeGb, pct))
        } catch (_: Exception) {
            // Ignore
        }

        return lines
    }

    private fun getStorageSummary(): String {
        return try {
            val primaryDir = Environment.getExternalStorageDirectory()
            val stat = StatFs(primaryDir.path)
            val total = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            val free = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            val used = total - free
            "$used GB used / $total GB total ($free GB free)"
        } catch (e: Exception) {
            "Storage access restricted or unavailable"
        }
    }

    fun getBatterySummary(context: Context): String {
        return try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bStatus = context.registerReceiver(null, ifilter)
            val level = bStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (scale > 0 && level >= 0) (level * 100 / scale) else -1
            val status = bStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val plugged = bStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            val plugSource = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC Power"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                else -> "Battery Discharge"
            }
            val temp = (bStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            "$pct% [${if (isCharging) "Charging via $plugSource" else "Discharging"}], Temp: ${temp}°C"
        } catch (e: Exception) {
            "Battery status unavailable"
        }
    }
}
