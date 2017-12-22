/**
 * @author nik
 */
package org.nik.projectNecromancer

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.io.URLUtil
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import java.io.File
import java.util.*
import java.util.logging.Logger

val LOG = Logger.getLogger("org.nik.projectNecromancer")

fun createJar(fullPath: String) : File? {
    if (fullPath.isEmpty()) return null
    val pair = URLUtil.splitJarUrl(fullPath)
    if (pair == null) return null
    return null
}

fun createDirectory(path: String): File? {
    val file = File(path)
    if (!file.isAbsolute) {
        LOG.info("Root $path wasn't created because path is not absolute")
        return null
    }
    FileUtil.createDirectory(file)
    return file
}

class NameGenerator {
    private var count: Int = 0

    fun className() = "JavaClass${count++}"
    fun packageName() = "p${count++}"
}

fun createJavaFiles(dir: File, nameGen: NameGenerator) : File {
    val className = nameGen.className()
    val packageName = nameGen.packageName()
    val file = File(dir, "$packageName/$className.java")
    FileUtil.writeToFile(file, "package $packageName;\npublic class $className{\n}")
    return file
}

class ResurrectProjectAction : AnAction("Resurrect Project") {
    override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return

        val nameGen = NameGenerator()
        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) return

        object : Task.Backgroundable(project, "Resurrecting Project", true) {
            override fun run(indicator: ProgressIndicator) {
                val generatedFiles = ArrayList<File?>()
                for ((i, module) in modules.withIndex()) {
                    indicator.checkCanceled()
                    indicator.fraction = i.toDouble() / modules.size
                    indicator.text = "Module '${ModuleUtilCore.getModuleNameInReadAction(module)}'..."
                    ReadAction.run<RuntimeException> {
                        for (entry in ModuleRootManager.getInstance(module).contentEntries) {
                            createDirectory(VfsUtilCore.urlToPath(entry.url))
                            for (folder in entry.sourceFolders) {
                                val url = folder.url
                                if (url.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
                                    generatedFiles.add(createJar(VfsUtilCore.urlToPath(url)))
                                } else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
                                    val dir = createDirectory(VfsUtilCore.urlToPath(url))
                                    if (dir != null && JavaModuleSourceRootTypes.SOURCES.contains(folder.rootType)) {
                                        generatedFiles.add(createJavaFiles(dir, nameGen))
                                    }
                                }
                            }
                        }

                    }
                }
                LocalFileSystem.getInstance().refreshIoFiles(generatedFiles.filterNotNull())
            }
        }.queue()
    }


    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
