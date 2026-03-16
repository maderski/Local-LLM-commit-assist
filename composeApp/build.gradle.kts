import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
        }
    }
}


// Workaround for IntelliJ injecting a broken coroutine debug init script
// See: https://youtrack.jetbrains.com/issue/KTIJ-29068
gradle.taskGraph.whenReady {
    allTasks.filterIsInstance<JavaExec>().forEach { task ->
        task.jvmArgumentProviders.removeAll {
            it.javaClass.name.contains("KotlinCoroutineJvmDebugArgumentsProvider")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.maderskitech.localllmcommitassist.MainKt"

        nativeDistributions {
            val projectVersion = project.version.toString()
            val resolvedVersion = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() }
                ?: if (projectVersion.isBlank() || projectVersion == "unspecified") "1.2.0" else projectVersion
            targetFormats(TargetFormat.Dmg)
            packageName = "Local LLM Commit Assist"
            packageVersion = resolvedVersion
            val envJavaHome = System.getenv("JAVA_HOME")
            if (!envJavaHome.isNullOrBlank() && File(envJavaHome).exists()) {
                javaHome = envJavaHome
            }
            macOS {
                bundleID = "com.maderskitech.localllmcommitassist"
                appCategory = "public.app-category.developer-tools"
                iconFile.set(project.file("src/jvmMain/resources/app-icon.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSHighResolutionCapable</key>
                        <true/>
                    """.trimIndent()
                }
            }
        }
    }
}
