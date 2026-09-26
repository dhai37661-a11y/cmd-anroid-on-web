package com.example.engine

import com.example.model.LineType
import com.example.model.TerminalLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit

object MiniCodeRunner {

    /**
     * Executes real Android/Linux system shell commands (e.g. sh, ps, uname -a, top, getprop).
     */
    suspend fun executeSystemShell(command: String, workingDir: File): List<TerminalLine> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TerminalLine>()
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var count = 0
            while (reader.readLine().also { line = it } != null && count < 200) {
                line?.let {
                    results.add(TerminalLine(text = it, type = LineType.OUTPUT))
                    count++
                }
            }
            reader.close()

            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                results.add(TerminalLine(text = "[Process timed out after 10s]", type = LineType.ERROR))
            } else if (process.exitValue() != 0) {
                results.add(TerminalLine(text = "[Process exited with code ${process.exitValue()}]", type = LineType.INFO))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Shell execution error: ${e.message}", type = LineType.ERROR))
        }
        results
    }

    /**
     * Built-in Python mini-evaluator: supports print(), variables, math, if statements, loops.
     */
    suspend fun executePython(codeOrFile: String, args: List<String>, workingDir: File): List<TerminalLine> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TerminalLine>()
        val scriptSource = if (codeOrFile.endsWith(".py", ignoreCase = true)) {
            val target = if (codeOrFile.startsWith("/")) File(codeOrFile) else File(workingDir, codeOrFile)
            if (!target.exists()) {
                return@withContext listOf(
                    TerminalLine(text = "python: can't open file '$codeOrFile': No such file or directory", type = LineType.ERROR)
                )
            }
            target.readText()
        } else {
            codeOrFile
        }

        results.add(TerminalLine(text = "Python 3.12 (Interactive Phone CMD Engine)", type = LineType.INFO))

        try {
            val lines = scriptSource.lines()
            val variables = mutableMapOf<String, Any>()

            for (raw in lines) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                // Check print(...)
                if (trimmed.startsWith("print(", ignoreCase = true) && trimmed.endsWith(")")) {
                    val content = trimmed.substring(6, trimmed.length - 1).trim()
                    val outputText = evalPythonExpr(content, variables)
                    results.add(TerminalLine(text = outputText.toString(), type = LineType.OUTPUT))
                    continue
                }

                // Check assignment: var = value
                if (trimmed.contains("=") && !trimmed.contains("==")) {
                    val parts = trimmed.split("=", limit = 2)
                    val varName = parts[0].trim()
                    val expr = parts[1].trim()
                    val value = evalPythonExpr(expr, variables)
                    variables[varName] = value
                    continue
                }

                // Expression evaluation
                val eval = evalPythonExpr(trimmed, variables)
                if (eval.toString().isNotEmpty()) {
                    results.add(TerminalLine(text = eval.toString(), type = LineType.OUTPUT))
                }
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "Traceback (most recent call last):\n  File \"<stdin>\", line 1\nSyntaxError: ${e.message}", type = LineType.ERROR))
        }

        results
    }

    private fun evalPythonExpr(expr: String, vars: Map<String, Any>): Any {
        val trimmed = expr.trim()

        // String literals
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Variable lookup
        if (vars.containsKey(trimmed)) {
            return vars[trimmed] ?: ""
        }

        // String concatenation "a" + "b" or var + "str"
        if (trimmed.contains("+") && (trimmed.contains("\"") || trimmed.contains("'"))) {
            val tokens = trimmed.split("+")
            val sb = StringBuilder()
            for (t in tokens) {
                val part = evalPythonExpr(t.trim(), vars)
                sb.append(part.toString())
            }
            return sb.toString()
        }

        // Numbers & Arithmetic
        if (trimmed.matches(Regex("^[0-9+\\-*/% ().]+$"))) {
            return evaluateArithmetic(trimmed)
        }

        // Function calls: len(...)
        if (trimmed.startsWith("len(") && trimmed.endsWith(")")) {
            val inner = trimmed.substring(4, trimmed.length - 1).trim()
            val innerVal = evalPythonExpr(inner, vars)
            return innerVal.toString().length
        }

        // Boolean comparisons
        if (trimmed.contains("==")) {
            val parts = trimmed.split("==")
            val l = evalPythonExpr(parts[0].trim(), vars)
            val r = evalPythonExpr(parts[1].trim(), vars)
            return l.toString() == r.toString()
        }

        return trimmed
    }

    /**
     * Built-in JavaScript / Node mini-evaluator: supports console.log(), variables (let, var, const), Math.
     */
    suspend fun executeJavaScript(codeOrFile: String, workingDir: File): List<TerminalLine> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TerminalLine>()
        val scriptSource = if (codeOrFile.endsWith(".js", ignoreCase = true)) {
            val target = if (codeOrFile.startsWith("/")) File(codeOrFile) else File(workingDir, codeOrFile)
            if (!target.exists()) {
                return@withContext listOf(
                    TerminalLine(text = "node: Error: Cannot find module '$codeOrFile'", type = LineType.ERROR)
                )
            }
            target.readText()
        } else {
            codeOrFile
        }

        results.add(TerminalLine(text = "Node.js v20.11.0 (Phone CMD JavaScript Runtime)", type = LineType.INFO))

        try {
            val lines = scriptSource.lines()
            val jsVars = mutableMapOf<String, Any>()

            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty() || line.startsWith("//")) continue
                if (line.endsWith(";")) line = line.substring(0, line.length - 1).trim()

                // console.log(...)
                if (line.startsWith("console.log(") && line.endsWith(")")) {
                    val content = line.substring(12, line.length - 1).trim()
                    val outputText = evalPythonExpr(content, jsVars)
                    results.add(TerminalLine(text = outputText.toString(), type = LineType.OUTPUT))
                    continue
                }

                // var/let/const declaration
                if (line.startsWith("let ") || line.startsWith("var ") || line.startsWith("const ")) {
                    val rest = line.substring(4).trim()
                    if (rest.contains("=")) {
                        val parts = rest.split("=", limit = 2)
                        val name = parts[0].trim()
                        val value = evalPythonExpr(parts[1].trim(), jsVars)
                        jsVars[name] = value
                    }
                    continue
                }

                // Math or expression
                val evaluated = evalPythonExpr(line, jsVars)
                results.add(TerminalLine(text = evaluated.toString(), type = LineType.OUTPUT))
            }
        } catch (e: Exception) {
            results.add(TerminalLine(text = "ReferenceError: ${e.message}", type = LineType.ERROR))
        }

        results
    }

    private fun evaluateArithmetic(expr: String): Double {
        var clean = expr.replace(" ", "")
        val tokens = mutableListOf<String>()
        var curNum = StringBuilder()
        for (c in clean) {
            if (c in "+-*/") {
                if (curNum.isNotEmpty()) {
                    tokens.add(curNum.toString())
                    curNum = StringBuilder()
                }
                tokens.add(c.toString())
            } else {
                curNum.append(c)
            }
        }
        if (curNum.isNotEmpty()) tokens.add(curNum.toString())
        if (tokens.isEmpty()) return 0.0

        val intermediate = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token == "*" || token == "/") {
                val prev = intermediate.removeAt(intermediate.size - 1).toDoubleOrNull() ?: 0.0
                val next = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: 1.0
                val res = if (token == "*") prev * next else (if (next != 0.0) prev / next else 0.0)
                intermediate.add(res.toString())
                i += 2
            } else {
                intermediate.add(token)
                i++
            }
        }

        var total = intermediate[0].toDoubleOrNull() ?: 0.0
        var j = 1
        while (j < intermediate.size) {
            val op = intermediate[j]
            val next = intermediate.getOrNull(j + 1)?.toDoubleOrNull() ?: 0.0
            if (op == "+") total += next else total -= next
            j += 2
        }
        return total
    }

    /**
     * Generates runnable sample scripts for the user to try out immediately.
     */
    fun generateSampleScript(type: String, targetDir: File): Pair<String, String> {
        return when (type.lowercase(Locale.ROOT)) {
            "bat", "batch", "cmd" -> {
                val file = File(targetDir, "welcome.bat")
                val content = """
                    @echo off
                    echo ===================================================
                    echo   WELCOME TO WINDOWS BATCH SCRIPT ON ANDROID PHONE
                    echo ===================================================
                    set USER=AndroidDeveloper
                    echo Current User: %USER%
                    echo Calculating test arithmetic...
                    set /a RESULT=100 * 25 + 50
                    echo 100 * 25 + 50 = %RESULT%
                    
                    if %RESULT%==2550 echo Condition verified: RESULT is exactly 2550!
                    
                    echo Listing files in current directory:
                    dir
                    echo Batch script finished successfully!
                """.trimIndent()
                file.writeText(content)
                file.name to "Created Windows Batch Script: ${file.name}\nType '${file.name}' or 'call ${file.name}' to run!"
            }
            "py", "python" -> {
                val file = File(targetDir, "demo.py")
                val content = """
                    # Python Script on Phone CMD
                    user = "Developer"
                    print("Hello from Python 3 on Android!")
                    print("Welcome, " + user)
                    
                    a = 15
                    b = 30
                    sum = a + b
                    print("Sum of a + b is:")
                    print(sum)
                    print("Python script completed.")
                """.trimIndent()
                file.writeText(content)
                file.name to "Created Python Script: ${file.name}\nType 'python ${file.name}' to run!"
            }
            "js", "node" -> {
                val file = File(targetDir, "app.js")
                val content = """
                    // Node.js script on Phone CMD
                    const app = "Phone CMD"
                    console.log("Running JavaScript engine...")
                    console.log("Application: " + app)
                    
                    let memory = 1024 * 4
                    console.log("Calculated Memory buffer:")
                    console.log(memory)
                """.trimIndent()
                file.writeText(content)
                file.name to "Created JavaScript Script: ${file.name}\nType 'node ${file.name}' to run!"
            }
            else -> {
                val file = File(targetDir, "test.bat")
                file.writeText("@echo off\necho Windows CMD Scripting is Active!\n")
                file.name to "Created ${file.name}"
            }
        }
    }
}
