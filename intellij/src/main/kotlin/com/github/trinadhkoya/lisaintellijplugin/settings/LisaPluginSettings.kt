package com.github.trinadhkoya.lisaintellijplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "com.github.trinadhkoya.lisaintellijplugin.settings.LisaPluginSettings",
    storages = [Storage("LisaPluginSettings.xml")]
)
@Service(Service.Level.PROJECT)
class LisaPluginSettings : PersistentStateComponent<LisaPluginSettings.State> {

    data class State(
        var provider: String = "openai",
        var model: String = "gpt-4o",
        var openaiKey: String = "",
        var claudeKey: String = "",
        var geminiKey: String = "",
        var groqKey: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): LisaPluginSettings = project.getService(LisaPluginSettings::class.java)
    }
}
