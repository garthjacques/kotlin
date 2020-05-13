description = "JetPack Compose compiler plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    // TODO: Check for unnecessary deps

    compileOnly(project(":kotlin-gradle-plugin"))
    compileOnly(project(":kotlin-gradle-plugin-api"))

    compileOnly(intellijCoreDep()) { includeJars("intellij-core", "asm-all", rootProject = rootProject) }
    compileOnly(project(":compiler:plugin-api"))
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":compiler:backend"))
    compileOnly(project(":compiler:ir.backend.common"))
    compileOnly(project(":compiler:ir.psi2ir"))
    compileOnly(project(":compiler:backend.js"))
    compileOnly(project(":js:js.frontend"))
    compileOnly(project(":js:js.translator"))

    runtime(kotlinStdlib())
}

sourceSets {
    "main" { projectDefault() }
}

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest {
    workingDir = rootDir
}

apply(from = "$rootDir/gradle/kotlinPluginPublication.gradle.kts")

pluginBundle {
    plugins {
        create("composePlugin") {
            id = "org.jetbrains.compose.plugin"
            description = "Kotlin compiler plugin for Compose"
            displayName = "Kotlin compiler plugin for Compose"
        }
    }
}
