package org.Kloppie74.locoPillars.commands

import com.sun.management.OperatingSystemMXBean
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.Kloppie74.locoPillars.utils.MSG
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.math.min

class MemoryCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        // Heap usage
        val memoryBean = ManagementFactory.getMemoryMXBean()
        val heapUsage = memoryBean.heapMemoryUsage
        val heapUsed = heapUsage.used
        val heapMax = heapUsage.max
        val heapCommitted = heapUsage.committed
        val usagePercent = ((heapUsed * 100L) / heapMax).toInt()

        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))
        sender.sendMessage(MSG.chatColors("&bServer Heap Usage"))
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))
        sender.sendMessage(
            MSG.chatColors(
                "&7Heap Used: &f" + formatBytes(heapUsed) + " &7| Max: &f" + formatBytes(
                    heapMax
                )
            )
        )
        sender.sendMessage(MSG.chatColors("&7Heap Committed: &f" + formatBytes(heapCommitted)))
        sender.sendMessage(MSG.chatColors("&7Heap Usage: &f$usagePercent%"))
        sender.sendMessage(MSG.chatColors("&b" + getMemoryBar(usagePercent, 30)))
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // Top 5 Heap Usages by MemoryPool
        val pools = ManagementFactory.getMemoryPoolMXBeans()
        val poolUsages: MutableList<PoolUsage> = ArrayList()
        for (pool in pools) {
            val usage = pool.usage
            if (usage != null && usage.max > 0) {
                poolUsages.add(PoolUsage(pool.name, usage.used, usage.max))
            }
        }
        poolUsages.sortWith(java.util.Comparator { a: PoolUsage, b: PoolUsage -> java.lang.Long.compare(b.used, a.used) })
        sender.sendMessage(MSG.chatColors("&bTop 5 Heap Usages by Pool:"))
        for (i in 0 until min(5.0, poolUsages.size.toDouble()).toInt()) {
            val pu = poolUsages[i]
            val percent = ((pu.used * 100L) / pu.max).toInt()
            sender.sendMessage(
                MSG.chatColors(
                    "&7" + (i + 1) + ". &f" + pu.name + ": &f" + formatBytes(pu.used) + " &7/ &f" + formatBytes(
                        pu.max
                    ) + " &7(" + percent + "%)"
                )
            )
        }
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // Class loading info (no per-class memory usage possible)
        val classBean = ManagementFactory.getClassLoadingMXBean()
        sender.sendMessage(MSG.chatColors("&bClass Loader Stats:"))
        sender.sendMessage(MSG.chatColors("&7Loaded Classes: &f" + classBean.loadedClassCount))
        sender.sendMessage(MSG.chatColors("&7Total Loaded: &f" + classBean.totalLoadedClassCount))
        sender.sendMessage(MSG.chatColors("&7Unloaded: &f" + classBean.unloadedClassCount))
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // CPU info
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        val processors = osBean.availableProcessors
        var cpuLoad = -1.0
        val cpuName = System.getenv("PROCESSOR_IDENTIFIER")
        try {
            if (osBean is OperatingSystemMXBean) {
                cpuLoad = osBean.processCpuLoad * 100
            }
        } catch (ignored: Exception) {
        }

        sender.sendMessage(MSG.chatColors("&bCPU Information:"))
        sender.sendMessage(MSG.chatColors("&7CPU: &f" + (cpuName ?: "Unknown")))
        sender.sendMessage(MSG.chatColors("&7Available Processors: &f$processors"))
        sender.sendMessage(
            MSG.chatColors(
                "&7CPU Usage: &f" + (if (cpuLoad >= 0) String.format(
                    "%.2f",
                    cpuLoad
                ) + "%" else "N/A")
            )
        )
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // Thread info
        val threadBean = ManagementFactory.getThreadMXBean()
        val threadCount = threadBean.threadCount
        sender.sendMessage(MSG.chatColors("&bThread Information:"))
        sender.sendMessage(MSG.chatColors("&7Active Threads: &f$threadCount"))
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // Runtime info
        val uptime = ManagementFactory.getRuntimeMXBean().uptime
        val uptimeDays = uptime / (1000 * 60 * 60 * 24)
        val uptimeHours = (uptime / (1000 * 60 * 60)) % 24
        val uptimeMinutes = (uptime / (1000 * 60)) % 60
        val uptimeStr = StringBuilder()
        if (uptimeDays > 0) uptimeStr.append(uptimeDays).append("d ")
        uptimeStr.append(uptimeHours).append("h ").append(uptimeMinutes).append("m")

        sender.sendMessage(MSG.chatColors("&bRuntime Information:"))
        sender.sendMessage(MSG.chatColors("&7Server Uptime: &f$uptimeStr"))
        sender.sendMessage(MSG.chatColors("&7Java Version: &f" + System.getProperty("java.version")))
        sender.sendMessage(MSG.chatColors("&7OS: &f" + System.getProperty("os.name") + " " + System.getProperty("os.arch")))
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        // Player and game stats
        val onlinePlayers = Bukkit.getOnlinePlayers().size
        sender.sendMessage(MSG.chatColors("&bPlayer & Game Statistics:"))
        sender.sendMessage(MSG.chatColors("&7Online Players: &f$onlinePlayers"))
        sender.sendMessage(MSG.chatColors("&7Joins Since Restart: &f" + joinsSinceRestart))
        sender.sendMessage(MSG.chatColors("&7Games Since Restart: &f" + gamesSinceRestart))
        sender.sendMessage(MSG.chatColors("&7Games by Type:"))
        for ((key, value) in allGameCounts) {
            sender.sendMessage(MSG.chatColors("&7- $key: &f$value"))
        }
        sender.sendMessage(MSG.chatColors("&b-------------------------------------"))

        return true
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        val mb = bytes / (1024.0 * 1024.0)
        return if (gb >= 1) String.format("%.2f GB", gb)
        else String.format("%.2f MB", mb)
    }

    private fun getMemoryBar(percent: Int, length: Int): String {
        val filled = (length * (percent / 100.0)).toInt()
        val bar = StringBuilder("[")
        for (i in 0 until length) {
            if (i < filled) bar.append("|")
            else bar.append(" ")
        }
        bar.append("] ").append(percent).append("%")
        return bar.toString()
    }

    private class PoolUsage(var name: String, var used: Long, var max: Long)
    companion object {
        private val gameCountByName: MutableMap<String, Int> = ConcurrentHashMap<String, Int>()
        var gamesSinceRestart: Int = 0
            private set
        var joinsSinceRestart: Int = 0
            private set

        fun incrementJoins() {
            joinsSinceRestart++
        }

        private val ALLOWED_GAME_TYPES = setOf("tnt", "bed", "cpvp", "garden", "warden", "luckyblocks", "nerf")
        fun countExtraGame(gameName: String?) {
            gamesSinceRestart++
            if (gameName != null) {
                val lowerName = gameName.lowercase(Locale.getDefault())
                for (type in ALLOWED_GAME_TYPES) {
                    if (lowerName.contains(type)) {
                        gameCountByName.merge(type, 1) { a: Int?, b: Int? ->
                            Integer.sum(
                                a!!, b!!
                            )
                        }
                    }
                }
            }
        }

        fun getGamesSinceRestart(gameName: String): Int {
            return gameCountByName.getOrDefault(gameName.lowercase(Locale.getDefault()), 0)
        }

        val allGameCounts: Map<String, Int>
            get() = ConcurrentHashMap<String, Int>(gameCountByName)
    }
}