package com.github.trinadhkoya.lisaintellijplugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.trinadhkoya.lisaintellijplugin.MyBundle

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project, val scope: kotlinx.coroutines.CoroutineScope)
