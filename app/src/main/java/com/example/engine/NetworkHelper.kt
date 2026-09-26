package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getIpConfig(): List<String> = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        lines.add("Windows IP Configuration (Android Network Interfaces)")
        lines.add("")
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            var foundAny = false
            for (intf in interfaces) {
                if (!intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                if (addrs.isEmpty()) continue

                foundAny = true
                val isWifi = intf.name.startsWith("wlan", ignoreCase = true)
                val isCell = intf.name.startsWith("rmnet", ignoreCase = true) || intf.name.startsWith("ccmni", ignoreCase = true)
                val typeName = when {
                    isWifi -> "Wireless LAN adapter Wi-Fi"
                    isCell -> "Mobile Cellular adapter"
                    intf.isLoopback -> "Loopback adapter Localhost"
                    else -> "Ethernet adapter ${intf.displayName}"
                }

                lines.add("$typeName (${intf.name}):")
                val mac = try {
                    intf.hardwareAddress?.joinToString(":") { "%02X".format(it) } ?: "N/A"
                } catch (e: Exception) {
                    "N/A"
                }
                lines.add(String.format("   Physical Address. . . . . . . . . : %s", mac))

                for (addr in addrs) {
                    val hostAddress = addr.hostAddress ?: continue
                    if (addr.isLoopbackAddress && !intf.isLoopback) continue
                    if (hostAddress.contains(":")) {
                        // IPv6
                        val cleanIpv6 = hostAddress.substringBefore("%")
                        lines.add(String.format("   Link-local IPv6 Address . . . . . : %s", cleanIpv6))
                    } else {
                        // IPv4
                        lines.add(String.format("   IPv4 Address. . . . . . . . . . . : %s", hostAddress))
                    }
                }
                lines.add("")
            }
            if (!foundAny) {
                lines.add("   Media State . . . . . . . . . . . : Media disconnected")
            }
        } catch (e: Exception) {
            lines.add("Error querying network adapters: ${e.message}")
        }
        lines
    }

    suspend fun pingHost(host: String, count: Int = 4): List<String> = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        val target = host.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")

        lines.add("Pinging $target with 32 bytes of data:")
        var received = 0
        val times = mutableListOf<Long>()

        for (i in 1..count) {
            val startTime = System.currentTimeMillis()
            var success = false
            try {
                val socket = Socket()
                // Try port 80 or 443 socket connection as ICMP echo may require root on standard Android
                val addr = InetAddress.getByName(target)
                socket.connect(InetSocketAddress(addr, 80), 2000)
                socket.close()
                val duration = System.currentTimeMillis() - startTime
                times.add(duration)
                lines.add("Reply from ${addr.hostAddress}: bytes=32 time=${duration}ms TTL=64")
                received++
                success = true
            } catch (_: Exception) {
                // Try ICMP fallback
                try {
                    val addr = InetAddress.getByName(target)
                    val isReachable = addr.isReachable(2000)
                    val duration = System.currentTimeMillis() - startTime
                    if (isReachable) {
                        times.add(duration)
                        lines.add("Reply from ${addr.hostAddress}: bytes=32 time=${duration}ms TTL=64")
                        received++
                        success = true
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }

            if (!success) {
                lines.add("Request timed out.")
            }
            if (i < count) {
                kotlinx.coroutines.delay(500)
            }
        }

        val lost = count - received
        val lossPct = (lost * 100) / count
        lines.add("")
        lines.add("Ping statistics for $target:")
        lines.add("    Packets: Sent = $count, Received = $received, Lost = $lost ($lossPct% loss),")
        if (times.isNotEmpty()) {
            val min = times.minOrNull() ?: 0
            val max = times.maxOrNull() ?: 0
            val avg = times.average().toLong()
            lines.add("Approximate round trip times in milli-seconds:")
            lines.add("    Minimum = ${min}ms, Maximum = ${max}ms, Average = ${avg}ms")
        }
        lines
    }

    suspend fun downloadFile(url: String, destFile: File, onProgress: (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                onProgress("Connecting to $url...")
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    onProgress("HTTP Error ${response.code}: ${response.message}")
                    return@withContext false
                }

                val body = response.body ?: run {
                    onProgress("Empty response body.")
                    return@withContext false
                }

                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(destFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val sizeKb = totalBytesRead / 1024
                onProgress("Download completed: $sizeKb KB saved to ${destFile.name}")
                true
            } catch (e: Exception) {
                onProgress("Download failed: ${e.message}")
                false
            }
        }
}
