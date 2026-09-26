package com.example.engine

import com.example.model.LineType
import com.example.model.TerminalLine
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

class BatchScriptRunner(
    private val globalVariables: MutableMap<String, String>,
    private val executeSubCommand: suspend (String) -> List<TerminalLine>
) {
    suspend fun runScript(scriptFile: File, args: List<String> = emptyList()): List<TerminalLine> {
        if (!scriptFile.exists()) {
            return listOf(
                TerminalLine(
                    text = "The system cannot find the batch file: ${scriptFile.name}",
                    type = LineType.ERROR
                )
            )
        }

        val lines = try {
            scriptFile.readLines()
        } catch (e: Exception) {
            return listOf(
                TerminalLine(
                    text = "Error reading batch file: ${e.message}",
                    type = LineType.ERROR
                )
            )
        }

        return executeLines(lines, args, scriptFile.name)
    }

    suspend fun executeLines(
        lines: List<String>,
        args: List<String> = emptyList(),
        scriptName: String = "script.bat"
    ): List<TerminalLine> {
        val output = mutableListOf<TerminalLine>()
        val localVars = HashMap(globalVariables)

        // Script parameters %0, %1, %2...
        localVars["0"] = scriptName
        for (i in args.indices) {
            localVars[(i + 1).toString()] = args[i]
        }
        localVars["*"] = args.joinToString(" ")

        var echoOn = true
        var index = 0
        var iterations = 0
        val maxIterations = 5000 // Guard against infinite goto loops

        // Map label positions (case-insensitive)
        val labels = mutableMapOf<String, Int>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith(":") && !line.startsWith("::")) {
                val labelName = line.substring(1).trim().split(" ", "\t")[0].lowercase(Locale.ROOT)
                if (labelName.isNotEmpty() && !labels.containsKey(labelName)) {
                    labels[labelName] = i
                }
            }
        }

        while (index < lines.size) {
            iterations++
            if (iterations > maxIterations) {
                output.add(
                    TerminalLine(
                        text = "[Batch Runtime Warning: Execution stopped - possible infinite loop exceeded $maxIterations operations]",
                        type = LineType.ERROR
                    )
                )
                break
            }

            var rawLine = lines[index].trim()
            index++

            if (rawLine.isEmpty()) continue

            // Comments
            if (rawLine.startsWith("::") || rawLine.startsWith("rem ", ignoreCase = true) || rawLine.equals("rem", ignoreCase = true)) {
                continue
            }

            // Skip label definitions
            if (rawLine.startsWith(":")) {
                continue
            }

            // Silent execution with '@'
            var suppressEchoThisLine = false
            if (rawLine.startsWith("@")) {
                suppressEchoThisLine = true
                rawLine = rawLine.substring(1).trim()
            }

            // Echo on/off toggle
            if (rawLine.equals("echo off", ignoreCase = true)) {
                echoOn = false
                continue
            } else if (rawLine.equals("echo on", ignoreCase = true)) {
                echoOn = true
                continue
            }

            // Variable substitution (%VAR%)
            val expandedLine = substituteVariables(rawLine, localVars)

            if (echoOn && !suppressEchoThisLine) {
                output.add(TerminalLine(text = "> $expandedLine", type = LineType.COMMAND))
            }

            // Check GOTO
            if (expandedLine.startsWith("goto ", ignoreCase = true)) {
                var targetLabel = expandedLine.substring(5).trim().removePrefix(":").split(" ", "\t")[0].lowercase(Locale.ROOT)
                if (targetLabel.equals("eof", ignoreCase = true)) {
                    // :eof means exit script
                    break
                }
                val targetIndex = labels[targetLabel]
                if (targetIndex != null) {
                    index = targetIndex + 1
                    continue
                } else {
                    output.add(
                        TerminalLine(
                            text = "The system cannot find the batch label specified - $targetLabel",
                            type = LineType.ERROR
                        )
                    )
                    break
                }
            }

            // Check SET command
            if (expandedLine.startsWith("set ", ignoreCase = true)) {
                handleSetCommand(expandedLine.substring(4).trim(), localVars, output)
                continue
            }

            // Check IF condition
            if (expandedLine.startsWith("if ", ignoreCase = true)) {
                val ifResult = handleIfCondition(expandedLine.substring(3).trim(), localVars)
                if (ifResult.conditionMet) {
                    if (ifResult.commandToRun.isNotEmpty()) {
                        if (ifResult.commandToRun.startsWith("goto ", ignoreCase = true)) {
                            val label = ifResult.commandToRun.substring(5).trim().removePrefix(":").lowercase(Locale.ROOT)
                            if (label == "eof") break
                            val targetIndex = labels[label]
                            if (targetIndex != null) {
                                index = targetIndex + 1
                                continue
                            }
                        } else {
                            val res = executeSubCommand(ifResult.commandToRun)
                            output.addAll(res)
                        }
                    }
                }
                continue
            }

            // Check PAUSE
            if (expandedLine.equals("pause", ignoreCase = true)) {
                output.add(TerminalLine(text = "Press any key to continue . . .", type = LineType.INFO))
                continue
            }

            // Check EXIT
            if (expandedLine.equals("exit", ignoreCase = true) || expandedLine.startsWith("exit ", ignoreCase = true)) {
                break
            }

            // Run standard command
            val cmdResults = executeSubCommand(expandedLine)
            output.addAll(cmdResults)
        }

        // Sync variables back
        globalVariables.putAll(localVars)
        return output
    }

    private fun substituteVariables(line: String, vars: Map<String, String>): String {
        var result = line
        // Substitute %VAR%
        val regex = Regex("%([a-zA-Z0-9_*]+)%")
        result = regex.replace(result) { match ->
            val key = match.groupValues[1]
            vars[key] ?: System.getenv(key) ?: match.value
        }

        // Substitute %1, %2, etc.
        val paramRegex = Regex("%([0-9*])")
        result = paramRegex.replace(result) { match ->
            val key = match.groupValues[1]
            vars[key] ?: ""
        }
        return result
    }

    private fun handleSetCommand(
        expr: String,
        vars: MutableMap<String, String>,
        output: MutableList<TerminalLine>
    ) {
        if (expr.isEmpty()) {
            // Display all environment variables
            vars.keys.sorted().forEach { key ->
                output.add(TerminalLine(text = "$key=${vars[key]}", type = LineType.OUTPUT))
            }
            return
        }

        if (expr.startsWith("/a ", ignoreCase = true) || expr.startsWith("/a\t", ignoreCase = true)) {
            // Arithmetic: set /a VAR = 10 + 20
            val mathExpr = expr.substring(3).trim()
            val eqIndex = mathExpr.indexOf('=')
            if (eqIndex != -1) {
                val varName = mathExpr.substring(0, eqIndex).trim()
                val mathPart = mathExpr.substring(eqIndex + 1).trim()
                try {
                    val evaluated = evaluateMath(mathPart, vars)
                    vars[varName] = evaluated.toString()
                    output.add(TerminalLine(text = "$varName = $evaluated", type = LineType.OUTPUT))
                } catch (e: Exception) {
                    output.add(TerminalLine(text = "Invalid mathematical expression: $mathPart", type = LineType.ERROR))
                }
            }
            return
        }

        // Standard: set VAR=value
        val eqIndex = expr.indexOf('=')
        if (eqIndex != -1) {
            val varName = expr.substring(0, eqIndex).trim()
            val varValue = expr.substring(eqIndex + 1).trim().removeSurrounding("\"")
            if (varValue.isEmpty()) {
                vars.remove(varName)
            } else {
                vars[varName] = varValue
            }
        } else {
            // Query variables matching prefix
            val matches = vars.filter { it.key.startsWith(expr, ignoreCase = true) }
            if (matches.isEmpty()) {
                output.add(TerminalLine(text = "Environment variable $expr not defined", type = LineType.ERROR))
            } else {
                matches.forEach { output.add(TerminalLine(text = "${it.key}=${it.value}", type = LineType.OUTPUT)) }
            }
        }
    }

    private data class IfParseResult(val conditionMet: Boolean, val commandToRun: String)

    private fun handleIfCondition(ifContent: String, vars: Map<String, String>): IfParseResult {
        var content = ifContent.trim()
        var isNot = false
        if (content.startsWith("not ", ignoreCase = true)) {
            isNot = true
            content = content.substring(4).trim()
        }

        // Check for == comparison
        if (content.contains("==")) {
            val parts = content.split("==", limit = 2)
            val left = parts[0].trim().removeSurrounding("\"")
            val rightPart = parts[1].trim()
            
            val rightTokens = rightPart.split(" ", limit = 2)
            val rightVal = rightTokens[0].trim().removeSurrounding("\"")
            val cmd = if (rightTokens.size > 1) rightTokens[1].trim() else ""

            val equals = left.equals(rightVal, ignoreCase = true)
            val conditionMet = if (isNot) !equals else equals
            return IfParseResult(conditionMet, cmd)
        }

        // Check: if exist filename command
        if (content.startsWith("exist ", ignoreCase = true)) {
            val rest = content.substring(6).trim()
            val tokens = rest.split(" ", limit = 2)
            val filePath = tokens[0].trim().removeSurrounding("\"")
            val cmd = if (tokens.size > 1) tokens[1].trim() else ""
            val exists = File(filePath).exists()
            val conditionMet = if (isNot) !exists else exists
            return IfParseResult(conditionMet, cmd)
        }

        // Check: if defined VAR command
        if (content.startsWith("defined ", ignoreCase = true)) {
            val rest = content.substring(8).trim()
            val tokens = rest.split(" ", limit = 2)
            val varName = tokens[0].trim()
            val cmd = if (tokens.size > 1) tokens[1].trim() else ""
            val isDefined = vars.containsKey(varName)
            val conditionMet = if (isNot) !isDefined else isDefined
            return IfParseResult(conditionMet, cmd)
        }

        return IfParseResult(false, "")
    }

    private fun evaluateMath(expr: String, vars: Map<String, String>): Long {
        var clean = expr.replace(" ", "")
        // Replace variable tokens
        vars.forEach { (k, v) ->
            val num = v.toLongOrNull() ?: 0L
            clean = clean.replace(k, num.toString())
        }

        // Simple arithmetic parser
        val numbers = mutableListOf<Long>()
        val ops = mutableListOf<Char>()
        var curNum = StringBuilder()

        for (c in clean) {
            if (c in "+-*/%") {
                if (curNum.isNotEmpty()) {
                    numbers.add(curNum.toString().toLongOrNull() ?: 0L)
                    curNum = StringBuilder()
                }
                ops.add(c)
            } else {
                curNum.append(c)
            }
        }
        if (curNum.isNotEmpty()) {
            numbers.add(curNum.toString().toLongOrNull() ?: 0L)
        }

        if (numbers.isEmpty()) return 0L

        // Handle * / %
        val finalNums = mutableListOf<Long>()
        val finalOps = mutableListOf<Char>()
        finalNums.add(numbers[0])

        for (i in ops.indices) {
            val op = ops[i]
            val nextNum = if (i + 1 < numbers.size) numbers[i + 1] else 0L
            if (op == '*' || op == '/' || op == '%') {
                val prev = finalNums.removeAt(finalNums.size - 1)
                val res = when (op) {
                    '*' -> prev * nextNum
                    '/' -> if (nextNum != 0L) prev / nextNum else 0L
                    '%' -> if (nextNum != 0L) prev % nextNum else 0L
                    else -> 0L
                }
                finalNums.add(res)
            } else {
                finalOps.add(op)
                finalNums.add(nextNum)
            }
        }

        var total = finalNums[0]
        for (i in finalOps.indices) {
            val op = finalOps[i]
            val next = finalNums[i + 1]
            if (op == '+') total += next else total -= next
        }
        return total
    }
}
