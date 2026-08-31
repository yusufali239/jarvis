package com.example.domain.tools

import android.content.Context
import com.example.android.apps.AppManager
import com.example.android.system.SystemController
import com.example.core.ai.GeminiToolDeclarationWrapper
import com.example.data.memory.MemoryManager
import com.example.domain.tools.impl.ClickElementTool
import com.example.domain.tools.impl.ClipboardTool
import com.example.domain.tools.impl.CreateReminderTool
import com.example.domain.tools.impl.FlashlightTool
import com.example.domain.tools.impl.GetDeviceInfoTool
import com.example.domain.tools.impl.GetTimeTool
import com.example.domain.tools.impl.LaunchUrlTool
import com.example.domain.tools.impl.OpenAppTool
import com.example.domain.tools.impl.PressBackTool
import com.example.domain.tools.impl.PressHomeTool
import com.example.domain.tools.impl.ReadNotificationsTool
import com.example.domain.tools.impl.ReadScreenTool
import com.example.domain.tools.impl.RecallFactTool
import com.example.domain.tools.impl.RememberFactTool
import com.example.domain.tools.impl.ScrollTool
import com.example.domain.tools.impl.TypeTextTool

class ToolRegistry(
    private val context: Context,
    private val appManager: AppManager,
    private val systemController: SystemController,
    private val memoryManager: MemoryManager
) {
    private val tools = mutableMapOf<String, JarvisTool>()

    init {
        registerTool(OpenAppTool(appManager))
        registerTool(LaunchUrlTool(context))
        registerTool(ReadScreenTool())
        registerTool(ClickElementTool())
        registerTool(TypeTextTool())
        registerTool(ScrollTool())
        registerTool(PressBackTool())
        registerTool(PressHomeTool())
        registerTool(GetDeviceInfoTool(systemController))
        registerTool(GetTimeTool(systemController))
        registerTool(FlashlightTool(systemController))
        registerTool(ClipboardTool(systemController))
        registerTool(ReadNotificationsTool())
        registerTool(CreateReminderTool(context))
        registerTool(RememberFactTool(memoryManager))
        registerTool(RecallFactTool(memoryManager))
    }

    fun registerTool(tool: JarvisTool) {
        tools[tool.name.lowercase()] = tool
    }

    fun getTool(name: String): JarvisTool? {
        return tools[name.lowercase()]
    }

    fun getAllTools(): List<JarvisTool> = tools.values.toList()

    fun getGeminiToolDeclarations(): List<GeminiToolDeclarationWrapper> {
        val declarations = tools.values.map { it.toGeminiDeclaration() }
        return listOf(GeminiToolDeclarationWrapper(declarations))
    }

    suspend fun executeTool(name: String, args: Map<String, Any?>): ToolExecutionResult {
        val tool = getTool(name)
            ?: return ToolExecutionResult(false, "Tool '$name' not found in JARVIS registry.")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolExecutionResult(false, "Execution error in tool '$name': ${e.message}")
        }
    }
}
