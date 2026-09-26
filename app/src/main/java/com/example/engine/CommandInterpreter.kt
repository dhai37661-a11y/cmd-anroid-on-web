package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.model.FileEntry
import com.example.model.LineType
import com.example.model.PromptStyle
import com.example.model.TerminalLine
import com.example.model.TerminalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommandInterpreter(
    private val context: Context,
    private val onThemeChange: (TerminalTheme) -> Unit,
    private val onTitleChange: (String) -> Unit,
    private val onOpenEditor: (File) -> Unit,
    private val onTriggerMatrix: () -> Unit,
    private val onRequestStoragePermission: () -> Unit
) {
    // Current working directory
    var currentDir: File = getInitialDirectory()
        private set

    var promptStyle: PromptStyle = PromptStyle.WINDOWS

    // Persistent CMD Environment Variables
    private val environmentVariables: MutableMap<String, String> = mutableMapOf(
        "OS" to "Windows_NT",
        "PROCESSOR_ARCHITECTURE" to (System.getProperty("os.arch") ?: "ARM64"),
        "NUMBER_OF_PROCESSORS" to Runtime.getRuntime().availableProcessors().toString(),
        "USERNAME" to (System.getProperty("user.name") ?: "Android"),
        "USERPROFILE" to "C:\\Users\\Android",
        "HOMEDRIVE" to "C:",
        "HOMEPATH" to "\\Users\\Android",
        "SYSTEMROOT" to "C:\\Windows",
        "WINDIR" to "C:\\Windows",
        "PATH" to "C:\\Windows\\System32;C:\\Python312;C:\\NodeJS"
    )

    private val batchRunner = BatchScriptRunner(environmentVariables) { subCmd ->
        execute(subCmd)
    }

    companion object {
        val STORAGE_ROOT: File = Environment.getExternalStorageDirectory()
        val DOWNLOAD_DIR: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    private fun getInitialDirectory(): File {
        return if (DOWNLOAD_DIR.exists() && DOWNLOAD_DIR.canRead()) {
            DOWNLOAD_DIR
        } else if (STORAGE_ROOT.exists() && STORAGE_ROOT.canRead()) {
            STORAGE_ROOT
        } else {
            context.filesDir
        }
    }

    fun getPromptString(): String {
        return when (promptStyle) {
            PromptStyle.WINDOWS -> {
                val winPath = toWindowsPath(currentDir)
                "$winPath>"
            }
            PromptStyle.LINUX -> {
                val linuxPath = currentDir.absolutePath
                "android@phone:$linuxPath$ "
            }
            PromptStyle.SHORT -> "CMD> "
        }
    }

    fun toWindowsPath(file: File): String {
        val rootPath = STORAGE_ROOT.absolutePath
        val filePath = file.absolutePath
        return if (filePath.startsWith(rootPath)) {
            val rel = filePath.removePrefix(rootPath).replace('/', '\\')
            if (rel.isEmpty() || rel == "\\") "C:\\" else "C:\\Users\\Android$rel"
        } else {
            "C:" + filePath.replace('/', '\\')
        }
    }

    private fun resolvePath(inputPath: String): File {
        var clean = inputPath.trim().removeSurrounding("\"").removeSurrounding("'")
        if (clean == "." || clean.isEmpty()) return currentDir
        if (clean == "..") return currentDir.parentFile ?: currentDir
        if (clean == "\\" || clean == "/" || clean.equals("c:\\", ignoreCase = true) || clean.equals("c:", ignoreCase = true)) {
            return STORAGE_ROOT
        }
        if (clean.equals("~", ignoreCase = true) || clean.equals("home", ignoreCase = true)) {
            return STORAGE_ROOT
        }
        if (clean.equals("download", ignoreCase = true) || clean.equals("downloads", ignoreCase = true)) {
            return if (DOWNLOAD_DIR.exists()) DOWNLOAD_DIR else File(STORAGE_ROOT, "Download")
        }

        // Check if Windows path e.g. C:\Users\Android\Download
        if (clean.startsWith("C:\\", ignoreCase = true) || clean.startsWith("C:/", ignoreCase = true)) {
            var sub = clean.substring(3).replace('\\', '/')
            if (sub.startsWith("Users/Android/", ignoreCase = true)) {
                sub = sub.substring("Users/Android/".length)
            } else if (sub.equals("Users/Android", ignoreCase = true)) {
                sub = ""
            }
            return if (sub.isEmpty()) STORAGE_ROOT else File(STORAGE_ROOT, sub)
        }

        // Absolute path
        if (clean.startsWith("/")) {
            return File(clean)
        }

        // Relative path
        clean = clean.replace('\\', '/')
        return File(currentDir, clean)
    }

    suspend fun execute(rawCommand: String): List<TerminalLine> = withContext(Dispatchers.IO) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // Handle redirection: e.g. echo hello > test.txt or >>
        if (trimmed.contains(" > ") || trimmed.contains(" >> ")) {
            return@withContext handleRedirection(trimmed)
        }

        // Tokenize command line with quote handling
        val tokens = parseTokens(trimmed)
        if (tokens.isEmpty()) return@withContext emptyList()

        val cmd = tokens[0].lowercase(Locale.ROOT)
        val args = tokens.drop(1)

        val results = mutableListOf<TerminalLine>()

        // 1. Check if executing a .bat or .cmd script directly (e.g. "welcome.bat" or "run.cmd")
        val possibleScriptFile = resolvePath(tokens[0])
        if (possibleScriptFile.exists() && (possibleScriptFile.extension.equals("bat", ignoreCase = true) || possibleScriptFile.extension.equals("cmd", ignoreCase = true))) {
            return@withContext batchRunner.runScript(possibleScriptFile, args)
        }

        try {
            when (cmd) {
                // Batch script execution
                "call" -> {
                    if (args.isEmpty()) {
                        results.add(TerminalLine(text = "Usage: call <script.bat> [arguments]", type = LineType.ERROR))
                    } else {
                        val scriptFile = resolvePath(args[0])
                        val scriptArgs = args.drop(1)
                        results.addAll(batchRunner.runScript(scriptFile, scriptArgs))
                    }
                }
                "set" -> {
                    val expr = args.joinToString(" ")
                    handleSetCommand(expr, results)
                }

                // Python Code Engine
                "python", "py", "python3" -> {
                    if (args.isEmpty()) {
                        results.add(TerminalLine(text = "Python 3.12 (Interactive Mode)", type = LineType.INFO))
                        results.add(TerminalLine(text = "Usage: python <script.py> OR python -c \"print('Hello')\"", type = LineType.INFO))
                    } else if (args[0] == "-c" && args.size > 1) {
                        val inlineCode = args.drop(1).joinToString(" ").removeSurrounding("\"")
                        results.addAll(MiniCodeRunner.executePython(inlineCode, emptyList(), currentDir))
                    } else {
                        val scriptTarget = args[0]
                        results.addAll(MiniCodeRunner.executePython(scriptTarget, args.drop(1), currentDir))
                    }
                }

                // JavaScript / Node.js Engine
                "node", "js" -> {
                    if (args.isEmpty()) {
                        results.add(TerminalLine(text = "Node.js v20.11.0", type = LineType.INFO))
                        results.add(TerminalLine(text = "Usage: node <script.js> OR node -e \"console.log('Hello')\"", type = LineType.INFO))
                    } else if (args[0] == "-e" && args.size > 1) {
                        val inlineCode = args.drop(1).joinToString(" ").removeSurrounding("\"")
                        results.addAll(MiniCodeRunner.executeJavaScript(inlineCode, currentDir))
                    } else {
                        val scriptTarget = args[0]
                        results.addAll(MiniCodeRunner.executeJavaScript(scriptTarget, currentDir))
                    }
                }

                // Real Android / Linux Shell Execution
                "sh", "bash" -> {
                    if (args.isEmpty()) {
                        results.add(TerminalLine(text = "Android Linux Shell Runner", type = LineType.INFO))
                        results.add(TerminalLine(text = "Usage: sh <linux_command> (e.g. sh uname -a, sh ps, sh getprop)", type = LineType.INFO))
                    } else {
                        val fullShellCmd = args.joinToString(" ")
                        results.addAll(MiniCodeRunner.executeSystemShell(fullShellCmd, currentDir))
                    }
                }

                // Demo code generator
                "demo", "create" -> {
                    val type = if (args.isNotEmpty()) args[0] else "bat"
                    val (fileName, msg) = MiniCodeRunner.generateSampleScript(type, currentDir)
                    results.add(TerminalLine(text = msg, type = LineType.SUCCESS))
                }

                // Standard File & Directory Commands
                "dir", "ls" -> executeDir(args, results)
                "cd", "chdir" -> executeCd(args, results)
                "md", "mkdir" -> executeMd(args, results)
                "rd", "rmdir" -> executeRd(args, results)
                "del", "rm", "erase" -> executeDel(args, results)
                "ren", "rename" -> executeRen(args, results)
                "copy", "cp" -> executeCopy(args, results)
                "move", "mv" -> executeMove(args, results)
                "type", "cat" -> executeType(args, results)
                "edit", "notepad", "nano" -> executeEdit(args, results)
                "echo" -> executeEcho(args, results)
                "tree" -> executeTree(args, results)
                "find", "findstr", "grep" -> executeFind(args, results)
                "attrib" -> executeAttrib(args, results)
                "touch" -> executeTouch(args, results)
                "head" -> executeHead(args, results)
                "tail" -> executeTail(args, results)
                "start", "open" -> executeStart(args, results)
                "hash", "md5", "sha256" -> executeHash(args, results)
                "cls", "clear" -> {
                    results.add(TerminalLine(text = "__CLEAR_SCREEN__", type = LineType.INFO))
                }

                // System Information & Diagnostics
                "systeminfo" -> {
                    val lines = SystemHardwareInfo.getSystemInfo(context)
                    lines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
                }
                "ver" -> {
                    results.add(TerminalLine(text = "Microsoft Windows [Version 11.0.22631.3296]", type = LineType.OUTPUT))
                    results.add(TerminalLine(text = "(Android ${Build.VERSION.RELEASE} Subsystem for PC Command Prompt)", type = LineType.INFO))
                }
                "whoami" -> {
                    val user = environmentVariables["USERNAME"] ?: "Android"
                    results.add(TerminalLine(text = "desktop-phone\\$user", type = LineType.OUTPUT))
                }
                "date" -> {
                    val sdf = SimpleDateFormat("EEE MM/dd/yyyy", Locale.US)
                    results.add(TerminalLine(text = "The current date is: ${sdf.format(Date())}", type = LineType.OUTPUT))
                }
                "time" -> {
                    val sdf = SimpleDateFormat("hh:mm:ss.SS a", Locale.US)
                    results.add(TerminalLine(text = "The current time is: ${sdf.format(Date())}", type = LineType.OUTPUT))
                }
                "mem", "free" -> {
                    val lines = SystemHardwareInfo.getMemoryDetail(context)
                    lines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
                }
                "diskpart", "df" -> {
                    val lines = SystemHardwareInfo.getDiskSummary()
                    lines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
                }
                "battery" -> {
                    val batteryStr = SystemHardwareInfo.getBatterySummary(context)
                    results.add(TerminalLine(text = "Battery Status: $batteryStr", type = LineType.OUTPUT))
                }
                "ipconfig", "ifconfig" -> {
                    val lines = NetworkHelper.getIpConfig()
                    lines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
                }
                "ping" -> {
                    if (args.isEmpty()) {
                        results.add(TerminalLine(text = "Usage: ping <hostname or IP address>", type = LineType.ERROR))
                    } else {
                        val host = args[0]
                        val lines = NetworkHelper.pingHost(host)
                        lines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
                    }
                }
                "curl", "download", "wget" -> executeDownload(args, results)
                "color" -> executeColor(args, results)
                "title" -> {
                    val title = args.joinToString(" ")
                    if (title.isNotBlank()) {
                        onTitleChange(title)
                        results.add(TerminalLine(text = "Window title set to '$title'", type = LineType.SUCCESS))
                    } else {
                        results.add(TerminalLine(text = "Usage: title <new title>", type = LineType.ERROR))
                    }
                }
                "calc" -> executeCalc(args, results)
                "matrix" -> {
                    results.add(TerminalLine(text = "Entering The Matrix...", type = LineType.SUCCESS))
                    onTriggerMatrix()
                }
                "storage", "perm" -> executeStorage(results)
                "help", "/?" -> executeHelp(args, results)
                else -> {
                    results.add(
                        TerminalLine(
                            text = "'$cmd' is not recognized as an internal or external command,",
                            type = LineType.ERROR
                        )
                    )
                    results.add(
                        TerminalLine(
                            text = "operable program or batch file. Type 'help' for available commands.",
                            type = LineType.INFO
                        )
                    )
                }
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Command Error: ${e.message}", type = LineType.ERROR))
        }

        results
    }

    private fun handleSetCommand(expr: String, results: MutableList<TerminalLine>) {
        if (expr.isEmpty()) {
            environmentVariables.keys.sorted().forEach { k ->
                results.add(TerminalLine(text = "$k=${environmentVariables[k]}", type = LineType.OUTPUT))
            }
            return
        }

        if (expr.startsWith("/a ", ignoreCase = true)) {
            val mathPart = expr.substring(3).trim()
            val eqIndex = mathPart.indexOf('=')
            if (eqIndex != -1) {
                val varName = mathPart.substring(0, eqIndex).trim()
                val mathExpr = mathPart.substring(eqIndex + 1).trim()
                try {
                    val calc = evaluateSimpleMath(mathExpr)
                    environmentVariables[varName] = calc.toLong().toString()
                    results.add(TerminalLine(text = "$varName = ${calc.toLong()}", type = LineType.SUCCESS))
                } catch (e: Exception) {
                    results.add(TerminalLine(text = "Math evaluation error: ${e.message}", type = LineType.ERROR))
                }
            }
            return
        }

        val eqIndex = expr.indexOf('=')
        if (eqIndex != -1) {
            val key = expr.substring(0, eqIndex).trim()
            val value = expr.substring(eqIndex + 1).trim()
            if (value.isEmpty()) {
                environmentVariables.remove(key)
                results.add(TerminalLine(text = "Removed variable $key", type = LineType.INFO))
            } else {
                environmentVariables[key] = value
                results.add(TerminalLine(text = "$key=$value", type = LineType.OUTPUT))
            }
        } else {
            val matches = environmentVariables.filter { it.key.startsWith(expr, ignoreCase = true) }
            if (matches.isEmpty()) {
                results.add(TerminalLine(text = "Environment variable $expr not defined", type = LineType.ERROR))
            } else {
                matches.forEach { results.add(TerminalLine(text = "${it.key}=${it.value}", type = LineType.OUTPUT)) }
            }
        }
    }

    private fun executeDir(args: List<String>, results: MutableList<TerminalLine>) {
        val targetDir = if (args.isNotEmpty() && !args[0].startsWith("/")) {
            resolvePath(args[0])
        } else {
            currentDir
        }

        if (!targetDir.exists()) {
            results.add(TerminalLine(text = "File Not Found", type = LineType.ERROR))
            return
        }
        if (!targetDir.isDirectory) {
            results.add(TerminalLine(text = "${targetDir.name} is a file, not a directory.", type = LineType.ERROR))
            return
        }

        val displayWinPath = toWindowsPath(targetDir)
        results.add(TerminalLine(text = " Volume in drive C is Internal Storage", type = LineType.OUTPUT))
        results.add(TerminalLine(text = " Volume Serial Number is 4F2A-88D1", type = LineType.OUTPUT))
        results.add(TerminalLine(text = "", type = LineType.OUTPUT))
        results.add(TerminalLine(text = " Directory of $displayWinPath", type = LineType.OUTPUT))
        results.add(TerminalLine(text = "", type = LineType.OUTPUT))

        val files = targetDir.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

        val dateFormat = SimpleDateFormat("MM/dd/yyyy  hh:mm a", Locale.US)
        val numFormat = NumberFormat.getNumberInstance(Locale.US)

        var fileCount = 0
        var dirCount = 0
        var totalBytes = 0L

        val curTime = dateFormat.format(Date(targetDir.lastModified()))
        results.add(TerminalLine(text = "$curTime    <DIR>          .", type = LineType.OUTPUT))
        dirCount++
        targetDir.parentFile?.let {
            val parentTime = dateFormat.format(Date(it.lastModified()))
            results.add(TerminalLine(text = "$parentTime    <DIR>          ..", type = LineType.OUTPUT))
            dirCount++
        }

        for (f in files) {
            val timeStr = dateFormat.format(Date(f.lastModified()))
            if (f.isDirectory) {
                dirCount++
                results.add(TerminalLine(text = "$timeStr    <DIR>          ${f.name}", type = LineType.OUTPUT))
            } else {
                fileCount++
                totalBytes += f.length()
                val sizeStr = String.format("%14s", numFormat.format(f.length()))
                results.add(TerminalLine(text = "$timeStr  $sizeStr ${f.name}", type = LineType.OUTPUT))
            }
        }

        val freeBytes = try {
            val stat = StatFs(targetDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            0L
        }

        results.add(TerminalLine(text = String.format("%16s File(s) %14s bytes", numFormat.format(fileCount), numFormat.format(totalBytes)), type = LineType.OUTPUT))
        results.add(TerminalLine(text = String.format("%16s Dir(s)  %14s bytes free", numFormat.format(dirCount), numFormat.format(freeBytes)), type = LineType.OUTPUT))
    }

    private fun executeCd(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = toWindowsPath(currentDir), type = LineType.OUTPUT))
            return
        }

        val target = resolvePath(args.joinToString(" "))
        if (!target.exists()) {
            results.add(TerminalLine(text = "The system cannot find the path specified.", type = LineType.ERROR))
            return
        }
        if (!target.isDirectory) {
            results.add(TerminalLine(text = "The directory name is invalid.", type = LineType.ERROR))
            return
        }
        currentDir = target
    }

    private fun executeMd(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: md <dir_name>", type = LineType.ERROR))
            return
        }
        val target = resolvePath(args.joinToString(" "))
        if (target.exists()) {
            results.add(TerminalLine(text = "A subdirectory or file ${target.name} already exists.", type = LineType.ERROR))
            return
        }
        val ok = target.mkdirs()
        if (ok) {
            results.add(TerminalLine(text = "Directory created: ${target.name}", type = LineType.SUCCESS))
        } else {
            results.add(TerminalLine(text = "Failed to create directory. Check storage permissions.", type = LineType.ERROR))
        }
    }

    private fun executeRd(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: rd <dir_name>", type = LineType.ERROR))
            return
        }
        val recursive = args.contains("/s") || args.contains("/S")
        val pathArg = args.filter { !it.equals("/s", ignoreCase = true) }.joinToString(" ")
        val target = resolvePath(pathArg)
        if (!target.exists()) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        if (!target.isDirectory) {
            results.add(TerminalLine(text = "${target.name} is a file, use DEL to remove it.", type = LineType.ERROR))
            return
        }
        val ok = if (recursive) target.deleteRecursively() else target.delete()
        if (ok) {
            results.add(TerminalLine(text = "Directory removed: ${target.name}", type = LineType.SUCCESS))
        } else {
            results.add(TerminalLine(text = "The directory is not empty. Use 'rd /s <dir>' to remove recursively.", type = LineType.ERROR))
        }
    }

    private fun executeDel(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: del <filename>", type = LineType.ERROR))
            return
        }
        val target = resolvePath(args.joinToString(" "))
        if (!target.exists()) {
            results.add(TerminalLine(text = "Could Not Find ${target.name}", type = LineType.ERROR))
            return
        }
        if (target.isDirectory) {
            results.add(TerminalLine(text = "${target.name} is a directory. Use RD to remove directories.", type = LineType.ERROR))
            return
        }
        val ok = target.delete()
        if (ok) {
            results.add(TerminalLine(text = "Deleted: ${target.name}", type = LineType.SUCCESS))
        } else {
            results.add(TerminalLine(text = "Access is denied: Unable to delete ${target.name}", type = LineType.ERROR))
        }
    }

    private fun executeRen(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.size < 2) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: ren <old_name> <new_name>", type = LineType.ERROR))
            return
        }
        val oldFile = resolvePath(args[0])
        if (!oldFile.exists()) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        val newName = args[1].trim().removeSurrounding("\"").removeSurrounding("'")
        val newFile = File(oldFile.parentFile ?: currentDir, newName)
        if (newFile.exists()) {
            results.add(TerminalLine(text = "A duplicate file name exists, or the file cannot be found.", type = LineType.ERROR))
            return
        }
        val ok = oldFile.renameTo(newFile)
        if (ok) {
            results.add(TerminalLine(text = "Renamed '${oldFile.name}' to '${newFile.name}'", type = LineType.SUCCESS))
        } else {
            results.add(TerminalLine(text = "Access is denied or rename operation failed.", type = LineType.ERROR))
        }
    }

    private fun executeCopy(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.size < 2) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: copy <source> <destination>", type = LineType.ERROR))
            return
        }
        val src = resolvePath(args[0])
        if (!src.exists() || src.isDirectory) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        var dest = resolvePath(args[1])
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        }
        try {
            src.copyTo(dest, overwrite = true)
            results.add(TerminalLine(text = "        1 file(s) copied.", type = LineType.SUCCESS))
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Copy failed: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeMove(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.size < 2) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: move <source> <destination>", type = LineType.ERROR))
            return
        }
        val src = resolvePath(args[0])
        if (!src.exists()) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        var dest = resolvePath(args[1])
        if (dest.isDirectory) {
            dest = File(dest, src.name)
        }
        try {
            val ok = src.renameTo(dest)
            if (ok) {
                results.add(TerminalLine(text = "        1 file(s) moved.", type = LineType.SUCCESS))
            } else {
                src.copyTo(dest, overwrite = true)
                src.delete()
                results.add(TerminalLine(text = "        1 file(s) moved.", type = LineType.SUCCESS))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Move failed: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeType(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: type <filename>", type = LineType.ERROR))
            return
        }
        val target = resolvePath(args.joinToString(" "))
        if (!target.exists()) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        if (target.isDirectory) {
            results.add(TerminalLine(text = "Access is denied: ${target.name} is a directory.", type = LineType.ERROR))
            return
        }
        try {
            val lines = target.readLines()
            if (lines.isEmpty()) {
                results.add(TerminalLine(text = "[Empty file: ${target.name}]", type = LineType.INFO))
                return
            }
            val maxDisplay = 150
            val displayLines = lines.take(maxDisplay)
            displayLines.forEach { results.add(TerminalLine(text = it, type = LineType.OUTPUT)) }
            if (lines.size > maxDisplay) {
                results.add(TerminalLine(text = "[... Truncated: Showing $maxDisplay of ${lines.size} lines]", type = LineType.INFO))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Error reading file: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeEdit(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "The syntax of the command is incorrect. Usage: edit <filename>", type = LineType.ERROR))
            return
        }
        val target = resolvePath(args.joinToString(" "))
        if (target.isDirectory) {
            results.add(TerminalLine(text = "Cannot edit a directory.", type = LineType.ERROR))
            return
        }
        results.add(TerminalLine(text = "Opening editor for ${target.name}...", type = LineType.SUCCESS))
        onOpenEditor(target)
    }

    private fun executeEcho(args: List<String>, results: MutableList<TerminalLine>) {
        var text = args.joinToString(" ")
        // Substitute %VAR%
        val regex = Regex("%([a-zA-Z0-9_*]+)%")
        text = regex.replace(text) { match ->
            val key = match.groupValues[1]
            environmentVariables[key] ?: System.getenv(key) ?: match.value
        }
        results.add(TerminalLine(text = text, type = LineType.OUTPUT))
    }

    private fun handleRedirection(command: String): List<TerminalLine> {
        val results = mutableListOf<TerminalLine>()
        val append = command.contains(" >> ")
        val parts = if (append) command.split(" >> ", limit = 2) else command.split(" > ", limit = 2)
        val left = parts[0].trim()
        val targetFilename = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")

        val targetFile = resolvePath(targetFilename)

        var content = if (left.startsWith("echo ", ignoreCase = true)) {
            left.substring(5)
        } else {
            left
        }

        // Variable substitution
        val regex = Regex("%([a-zA-Z0-9_*]+)%")
        content = regex.replace(content) { match ->
            val key = match.groupValues[1]
            environmentVariables[key] ?: match.value
        }

        try {
            if (append) {
                targetFile.appendText(content + "\n")
                results.add(TerminalLine(text = "Appended to ${targetFile.name}", type = LineType.SUCCESS))
            } else {
                targetFile.writeText(content + "\n")
                results.add(TerminalLine(text = "Written to ${targetFile.name}", type = LineType.SUCCESS))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Redirection failed: ${e.message}", type = LineType.ERROR))
        }
        return results
    }

    private fun executeTree(args: List<String>, results: MutableList<TerminalLine>) {
        val target = if (args.isNotEmpty()) resolvePath(args[0]) else currentDir
        results.add(TerminalLine(text = "Folder PATH listing for volume Internal Storage", type = LineType.OUTPUT))
        results.add(TerminalLine(text = toWindowsPath(target), type = LineType.OUTPUT))

        fun buildTree(dir: File, prefix: String, depth: Int) {
            if (depth > 3) return
            val list = dir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() } ?: return
            for (i in list.indices) {
                val isLast = i == list.size - 1
                val branch = if (isLast) "└── " else "├── "
                results.add(TerminalLine(text = "$prefix$branch${list[i].name}", type = LineType.OUTPUT))
                val nextPrefix = prefix + if (isLast) "    " else "│   "
                buildTree(list[i], nextPrefix, depth + 1)
            }
        }

        buildTree(target, "", 0)
    }

    private fun executeFind(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.size < 2) {
            results.add(TerminalLine(text = "Usage: find \"search_string\" <filename>", type = LineType.ERROR))
            return
        }
        val query = args[0].removeSurrounding("\"").removeSurrounding("'")
        val file = resolvePath(args[1])
        if (!file.exists()) {
            results.add(TerminalLine(text = "File not found: ${file.name}", type = LineType.ERROR))
            return
        }
        try {
            var matchCount = 0
            file.forEachLine { line ->
                if (line.contains(query, ignoreCase = true)) {
                    results.add(TerminalLine(text = line, type = LineType.OUTPUT))
                    matchCount++
                }
            }
            results.add(TerminalLine(text = "Found $matchCount match(es) in ${file.name}", type = LineType.INFO))
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Search error: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeAttrib(args: List<String>, results: MutableList<TerminalLine>) {
        val target = if (args.isNotEmpty()) resolvePath(args.joinToString(" ")) else currentDir
        if (!target.exists()) {
            results.add(TerminalLine(text = "File not found.", type = LineType.ERROR))
            return
        }
        val sdf = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
        val read = if (target.canRead()) "R" else " "
        val write = if (target.canWrite()) "W" else " "
        val exec = if (target.canExecute()) "X" else " "
        val dir = if (target.isDirectory) "D" else "F"
        val attrStr = "$read$write$exec $dir"
        results.add(TerminalLine(text = "$attrStr   ${sdf.format(Date(target.lastModified()))}   ${target.length()} bytes   ${target.name}", type = LineType.OUTPUT))
    }

    private fun executeTouch(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: touch <filename>", type = LineType.ERROR))
            return
        }
        val file = resolvePath(args.joinToString(" "))
        try {
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
                results.add(TerminalLine(text = "Updated timestamp for ${file.name}", type = LineType.SUCCESS))
            } else {
                file.createNewFile()
                results.add(TerminalLine(text = "Created empty file: ${file.name}", type = LineType.SUCCESS))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Touch failed: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeHead(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: head <filename> [count]", type = LineType.ERROR))
            return
        }
        val file = resolvePath(args[0])
        val count = args.getOrNull(1)?.toIntOrNull() ?: 10
        if (!file.exists()) {
            results.add(TerminalLine(text = "File not found.", type = LineType.ERROR))
            return
        }
        try {
            file.bufferedReader().useLines { lines ->
                lines.take(count).forEach {
                    results.add(TerminalLine(text = it, type = LineType.OUTPUT))
                }
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Read error: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeTail(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: tail <filename> [count]", type = LineType.ERROR))
            return
        }
        val file = resolvePath(args[0])
        val count = args.getOrNull(1)?.toIntOrNull() ?: 10
        if (!file.exists()) {
            results.add(TerminalLine(text = "File not found.", type = LineType.ERROR))
            return
        }
        try {
            val allLines = file.readLines()
            allLines.takeLast(count).forEach {
                results.add(TerminalLine(text = it, type = LineType.OUTPUT))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Read error: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun executeStart(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: start <filename>", type = LineType.ERROR))
            return
        }
        val file = resolvePath(args.joinToString(" "))
        if (!file.exists()) {
            results.add(TerminalLine(text = "The system cannot find the file specified.", type = LineType.ERROR))
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            results.add(TerminalLine(text = "Launched '${file.name}' with default application.", type = LineType.SUCCESS))
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setData(Uri.fromFile(file))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                results.add(TerminalLine(text = "Launched '${file.name}'.", type = LineType.SUCCESS))
            } catch (ex2: Exception) {
                results.add(TerminalLine(text = "No compatible app found to open ${file.name}", type = LineType.ERROR))
            }
        }
    }

    private fun executeHash(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: hash <filename>", type = LineType.ERROR))
            return
        }
        val file = resolvePath(args[0])
        if (!file.exists() || file.isDirectory) {
            results.add(TerminalLine(text = "File not found.", type = LineType.ERROR))
            return
        }
        try {
            val bytes = file.readBytes()
            val md = java.security.MessageDigest.getInstance("MD5")
            val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            val md5Str = md.digest(bytes).joinToString("") { "%02x".format(it) }
            val shaStr = sha256.digest(bytes).joinToString("") { "%02x".format(it) }
            results.add(TerminalLine(text = "MD5:    $md5Str", type = LineType.OUTPUT))
            results.add(TerminalLine(text = "SHA256: $shaStr", type = LineType.OUTPUT))
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Hash error: ${e.message}", type = LineType.ERROR))
        }
    }

    private suspend fun executeDownload(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: download <url> [destination_filename]", type = LineType.ERROR))
            return
        }
        val url = args[0]
        val filename = if (args.size > 1) {
            args[1]
        } else {
            url.substringAfterLast("/").substringBefore("?").ifEmpty { "downloaded_file" }
        }
        val destFile = resolvePath(filename)

        val progressList = mutableListOf<String>()
        val success = NetworkHelper.downloadFile(url, destFile) { msg ->
            progressList.add(msg)
        }
        progressList.forEach { results.add(TerminalLine(text = it, type = if (success) LineType.SUCCESS else LineType.ERROR)) }
    }

    private fun executeColor(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Sets default console foreground and background colors.", type = LineType.OUTPUT))
            results.add(TerminalLine(text = "Available themes: classic, matrix, amber, powershell, modern", type = LineType.INFO))
            return
        }
        val arg = args[0].lowercase(Locale.ROOT)
        val selectedTheme = when (arg) {
            "0a", "matrix", "green" -> TerminalTheme.MATRIX_GREEN
            "classic", "07", "black" -> TerminalTheme.CMD_CLASSIC
            "amber", "dos", "crt" -> TerminalTheme.CYBER_AMBER
            "powershell", "ps", "1f", "blue" -> TerminalTheme.POWERSHELL_BLUE
            "modern", "dark" -> TerminalTheme.MODERN_DARK
            else -> null
        }
        if (selectedTheme != null) {
            onThemeChange(selectedTheme)
            results.add(TerminalLine(text = "Switched to ${selectedTheme.displayName}", type = LineType.SUCCESS))
        } else {
            results.add(TerminalLine(text = "Unknown theme '$arg'. Choices: classic, matrix, amber, powershell, modern", type = LineType.ERROR))
        }
    }

    private fun executeCalc(args: List<String>, results: MutableList<TerminalLine>) {
        if (args.isEmpty()) {
            results.add(TerminalLine(text = "Usage: calc <expression> (e.g. calc 1024 * 768 / 100)", type = LineType.ERROR))
            return
        }
        val expr = args.joinToString(" ")
        try {
            val result = evaluateSimpleMath(expr)
            results.add(TerminalLine(text = "$expr = $result", type = LineType.SUCCESS))
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Calculation error: ${e.message}", type = LineType.ERROR))
        }
    }

    private fun evaluateSimpleMath(expr: String): Double {
        val sanitized = expr.replace(" ", "")
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (c in sanitized) {
            if (c in "+-*/") {
                if (currentNum.isNotEmpty()) {
                    tokens.add(currentNum.toString())
                    currentNum = StringBuilder()
                }
                tokens.add(c.toString())
            } else {
                currentNum.append(c)
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())

        val intermediate = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                val prev = intermediate.removeAt(intermediate.size - 1).toDouble()
                val next = tokens[i + 1].toDouble()
                val res = if (token == "*") prev * next else prev / next
                intermediate.add(res.toString())
                i += 2
            } else {
                intermediate.add(token)
                i++
            }
        }

        var total = intermediate[0].toDouble()
        var j = 1
        while (j < intermediate.size) {
            val op = intermediate[j]
            val next = intermediate[j + 1].toDouble()
            if (op == "+") total += next else total -= next
            j += 2
        }
        return total
    }

    private fun executeStorage(results: MutableList<TerminalLine>) {
        results.add(TerminalLine(text = "================ STORAGE ACCESS STATUS ================", type = LineType.INFO))
        results.add(TerminalLine(text = "Current Path : ${currentDir.absolutePath}", type = LineType.OUTPUT))
        results.add(TerminalLine(text = "Readable     : ${currentDir.canRead()}", type = LineType.OUTPUT))
        results.add(TerminalLine(text = "Writable     : ${currentDir.canWrite()}", type = LineType.OUTPUT))
        results.add(TerminalLine(text = "Download Dir : ${DOWNLOAD_DIR.absolutePath} (Accessible: ${DOWNLOAD_DIR.canRead()})", type = LineType.OUTPUT))
        
        val isAllFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        results.add(TerminalLine(text = "All Files Permission: ${if (isAllFilesGranted) "GRANTED (Full Phone Storage Access)" else "LIMITED (Tap Storage to grant full access)"}", type = if (isAllFilesGranted) LineType.SUCCESS else LineType.ERROR))
        if (!isAllFilesGranted) {
            results.add(TerminalLine(text = "Tip: You can grant All Files Access to freely browse and manage all downloaded files and SD Card folders.", type = LineType.INFO))
            onRequestStoragePermission()
        }
        results.add(TerminalLine(text = "=======================================================", type = LineType.INFO))
    }

    private fun executeHelp(args: List<String>, results: MutableList<TerminalLine>) {
        results.add(TerminalLine(text = "================ PHONE CMD & CODE ENGINE COMMANDS ================", type = LineType.INFO))
        val commands = listOf(
            "DIR / LS" to "List files and subdirectories.",
            "CD" to "Change current working directory (e.g. cd Download).",
            "MD / MKDIR" to "Create a new folder.",
            "RD / RMDIR" to "Remove a folder (supports /s recursive).",
            "DEL / RM" to "Delete file(s).",
            "REN" to "Rename file or folder.",
            "COPY / MOVE" to "Copy or move files to new destination.",
            "TYPE / CAT" to "Display text file contents directly.",
            "EDIT" to "Open full retro text editor to code/edit files.",
            "ECHO" to "Print text or write to file using > or >>.",
            "CALL <script.bat>" to "Execute a Windows Batch file with variables and labels.",
            "SET" to "View or define environment variables (set /a for math).",
            "PYTHON <file.py>" to "Run Python script or inline: python -c \"print('Hello')\".",
            "NODE <file.js>" to "Run JavaScript script or inline: node -e \"...\".",
            "SH <cmd>" to "Run native Android/Linux shell commands (e.g. sh uname -a).",
            "DEMO <bat|py|js>" to "Instantly generate sample runnable scripts to test.",
            "TREE" to "Display graphical directory tree structure.",
            "FIND" to "Search for text string inside a file.",
            "START" to "Open file with default Android application.",
            "SYSTEMINFO" to "Display full phone hardware, OS, CPU, RAM specs.",
            "IPCONFIG" to "Display network adapter IPs and MAC addresses.",
            "PING <host>" to "Ping internet host or IP address.",
            "DOWNLOAD <url>" to "Download files directly to current folder.",
            "CALC <math>" to "Calculator: evaluates + - * / math expressions.",
            "COLOR <theme>" to "Change console theme: classic, matrix, amber, powershell.",
            "MATRIX" to "Easter egg: Digital Rain Matrix code screen.",
            "CLS" to "Clear console screen."
        )

        for ((c, desc) in commands) {
            results.add(TerminalLine(text = String.format("%-18s %s", c, desc), type = LineType.OUTPUT))
        }
        results.add(TerminalLine(text = "==================================================================", type = LineType.INFO))
    }

    private fun parseTokens(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (c in command) {
            when (c) {
                '"' -> inQuotes = !inQuotes
                ' ' -> {
                    if (inQuotes) {
                        current.append(c)
                    } else if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }
        return tokens
    }

    fun listCurrentDirectoryFiles(): List<FileEntry> {
        val files = currentDir.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        return files.map { f ->
            FileEntry(
                name = f.name,
                path = f.absolutePath,
                isDirectory = f.isDirectory,
                size = f.length(),
                lastModified = f.lastModified(),
                isReadable = f.canRead(),
                isWritable = f.canWrite(),
                extension = f.extension
            )
        }
    }

    fun getTabCompletions(partial: String): List<String> {
        val trimmed = partial.trim()
        val lastWord = trimmed.substringAfterLast(" ").removeSurrounding("\"")
        val files = currentDir.listFiles() ?: return emptyList()
        return files.filter { it.name.startsWith(lastWord, ignoreCase = true) }
            .map { if (it.name.contains(" ")) "\"${it.name}\"" else it.name }
    }
}
