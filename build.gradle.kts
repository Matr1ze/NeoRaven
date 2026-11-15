plugins {
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
}

group = "xyz.daikirai"
version = "1.2"

java { toolchain.languageVersion.set(JavaLanguageVersion.of(8)) }

// In development runs FML looks for mcmod.info beside the classes. Put processed resources
// in the same output directory so the mod list shows metadata instead of "No mod information found".
sourceSets.named("main") {
    output.resourcesDir = layout.buildDirectory.dir("classes/java/main").get().asFile
}

loom {
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("clickgui.mixins.json")
    }
    mixin { defaultRefmapName.set("clickgui.refmap.json") }
    runConfigs {
        "client" {
            isIdeConfigGenerated = true
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--mixin", "clickgui.mixins.json")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
}

val embed: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    embed(files("libs/mixin-0.7.11-SNAPSHOT.jar"))
    // JLayer MP3 decoder (LGPL), embedded so the music player can decode NetEase streams on
    // Java 8; both jars go through the same zipTree() embed path as the mixin loader.
    embed(files("libs/jlayer.jar"))
    // ZXing core: the NetEase QR login code is generated client-side from the login URL.
    embed(files("libs/zxing-core-3.5.3.jar"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") { expand("version" to project.version) }
}
tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

tasks.named<JavaExec>("runClient") {
    systemProperty("neoraven.inputDiagnostics", "true")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

tasks.jar {
    from(embed.map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF", "LICENSE.txt")
        exclude("META-INF/services/javax.annotation.processing.Processor")
    }
    manifest.attributes(
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "TweakOrder" to "0",
        "MixinConfigs" to "clickgui.mixins.json",
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true"
    )
}

tasks.named<org.gradle.jvm.tasks.Jar>("remapJar") { archiveFileName.set("NeoRaven-${project.version}.jar") }

tasks.register<JavaExec>("startupPreview") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("xyz.daikirai.gui.onyx.menu.StartupAnimationPreview")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        val config = file(".gradle/loom-cache/launch.cfg")
        val natives = config.readLines().first { it.trim().startsWith("java.library.path=") }.substringAfter('=')
        systemProperty("org.lwjgl.librarypath", natives)
        systemProperty("java.library.path", natives)
    }
}

tasks.register<JavaExec>("shadowPreview") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("xyz.daikirai.gui.onyx.render.ShadowPreview")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        val config = file(".gradle/loom-cache/launch.cfg")
        val natives = config.readLines().first { it.trim().startsWith("java.library.path=") }.substringAfter('=')
        systemProperty("org.lwjgl.librarypath", natives)
        systemProperty("java.library.path", natives)
    }
}

// Runs the test-only Forge mod in an isolated game directory; no user profiles or music session.
tasks.register<JavaExec>("androidUiSmoke") {
    dependsOn(tasks.testClasses, "downloadAssets")
    mainClass.set("net.minecraft.launchwrapper.Launch")
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        var section = ""
        val launchArgs = mutableListOf<String>()
        file(".gradle/loom-cache/launch.cfg").forEachLine { line ->
            if (!line.startsWith("\t")) section = line.trim()
            else if (section == "commonProperties" || section == "clientProperties") {
                val entry = line.trim().split('=', limit = 2)
                if (entry.size == 2) systemProperty(entry[0], entry[1])
            } else if (section == "commonArgs" || section == "clientArgs") launchArgs.add(line.trim())
        }
        systemProperty("neoraven.uiSmoke", "true")
        args(launchArgs)
        args("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--version", "1.8.9", "--width", "1280", "--height", "720")
        workingDir = file("build/android-ui-smoke")
        workingDir.mkdirs()
        file("build/android-ui-smoke/keystrokes").mkdirs()
        file("build/android-ui-smoke/keystrokes/soar-setup-v1.done").writeText("")
        file("build/android-ui-smoke/PASS.txt").delete()
        file("build/android-ui-smoke/FAIL.txt").delete()
    }
    doLast {
        check(file("build/android-ui-smoke/PASS.txt").isFile && !file("build/android-ui-smoke/FAIL.txt").exists()) {
            "Android UI smoke test failed; see build/android-ui-smoke/logs/latest.log"
        }
    }
}

// Exercises native fullscreen input in an isolated game directory.
tasks.register<JavaExec>("fullscreenInputSmoke") {
    maxHeapSize = "768m"
    jvmArgs("-XX:ParallelGCThreads=2", "-XX:CICompilerCount=2")
    dependsOn(tasks.testClasses, "downloadAssets")
    mainClass.set("net.minecraft.launchwrapper.Launch")
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        var section = ""
        val launchArgs = mutableListOf<String>()
        file(".gradle/loom-cache/launch.cfg").forEachLine { line ->
            if (!line.startsWith("\t")) section = line.trim()
            else if (section == "commonProperties" || section == "clientProperties") {
                val entry = line.trim().split('=', limit = 2)
                if (entry.size == 2) systemProperty(entry[0], entry[1])
            } else if (section == "commonArgs" || section == "clientArgs") launchArgs.add(line.trim())
        }
        systemProperty("neoraven.fullscreenSmoke", "true")
        args(launchArgs)
        args("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--version", "1.8.9", "--width", "1280", "--height", "720")
        workingDir = file("build/fullscreen-input-smoke")
        workingDir.mkdirs()
        file("build/fullscreen-input-smoke/keystrokes").mkdirs()
        file("build/fullscreen-input-smoke/keystrokes/soar-setup-v1.done").writeText("")
        file("build/fullscreen-input-smoke/PASS.txt").delete()
        file("build/fullscreen-input-smoke/FAIL.txt").delete()
    }
    doLast {
        check(file("build/fullscreen-input-smoke/PASS.txt").isFile && !file("build/fullscreen-input-smoke/FAIL.txt").exists()) {
            "Fullscreen input smoke test failed; see build/fullscreen-input-smoke/logs/latest.log"
        }
    }
}

tasks.register<JavaExec>("glassResizeSmoke") {
    maxHeapSize = "768m"
    jvmArgs("-XX:ParallelGCThreads=2", "-XX:CICompilerCount=2")
    dependsOn(tasks.testClasses, "downloadAssets")
    mainClass.set("net.minecraft.launchwrapper.Launch")
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        var section = ""
        val launchArgs = mutableListOf<String>()
        file(".gradle/loom-cache/launch.cfg").forEachLine { line ->
            if (!line.startsWith("\t")) section = line.trim()
            else if (section == "commonProperties" || section == "clientProperties") {
                val entry = line.trim().split('=', limit = 2)
                if (entry.size == 2) systemProperty(entry[0], entry[1])
            } else if (section == "commonArgs" || section == "clientArgs") launchArgs.add(line.trim())
        }
        systemProperty("neoraven.glassSmoke", "true")
        args(launchArgs)
        args("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--version", "1.8.9", "--width", "1280", "--height", "720")
        workingDir = file("build/glass-resize-smoke")
        workingDir.mkdirs()
        file("build/glass-resize-smoke/keystrokes").mkdirs()
        file("build/glass-resize-smoke/keystrokes/soar-setup-v1.done").writeText("")
        file("build/glass-resize-smoke/PASS.txt").delete()
        file("build/glass-resize-smoke/FAIL.txt").delete()
    }
    doLast {
        check(file("build/glass-resize-smoke/PASS.txt").isFile && !file("build/glass-resize-smoke/FAIL.txt").exists()) {
            "Glass resize smoke test failed; see build/glass-resize-smoke/logs/latest.log"
        }
    }
}

// Drives the real first-run welcome flow in a virgin game directory and checks that its glass
// choices reach the ClickGUI settings, the live theme and the persisted preferences.
tasks.register<JavaExec>("welcomeGlassSmoke") {
    maxHeapSize = "768m"
    jvmArgs("-XX:ParallelGCThreads=2", "-XX:CICompilerCount=2")
    dependsOn(tasks.testClasses, "downloadAssets")
    mainClass.set("net.minecraft.launchwrapper.Launch")
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        var section = ""
        val launchArgs = mutableListOf<String>()
        file(".gradle/loom-cache/launch.cfg").forEachLine { line ->
            if (!line.startsWith("\t")) section = line.trim()
            else if (section == "commonProperties" || section == "clientProperties") {
                val entry = line.trim().split('=', limit = 2)
                if (entry.size == 2) systemProperty(entry[0], entry[1])
            } else if (section == "commonArgs" || section == "clientArgs") launchArgs.add(line.trim())
        }
        systemProperty("neoraven.welcomeSmoke", "true")
        args(launchArgs)
        args("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--version", "1.8.9", "--width", "1280", "--height", "720")
        workingDir = file("build/welcome-glass-smoke")
        workingDir.deleteRecursively()
        workingDir.mkdirs()
        // Virgin install: no setup marker, no config. Start with both glass switches off, the way
        // an install that once turned glass off looks, then turn liquid glass on in the guide.
        val prefs = file("build/welcome-glass-smoke/keystrokes/glass-theme.properties")
        prefs.parentFile.mkdirs()
        prefs.writeText(
            "theme=0\nfrost=0.88\nliquid=0.38\nmatte=0.65\ncolour=false\nmatteEnabled=false\n" +
            "frostEnabled=false\nliquidEnabled=false\naccent=-10070716\n"
        )
        file("build/welcome-glass-smoke/PASS.txt").delete()
        file("build/welcome-glass-smoke/FAIL.txt").delete()
    }
    doLast {
        check(file("build/welcome-glass-smoke/PASS.txt").isFile && !file("build/welcome-glass-smoke/FAIL.txt").exists()) {
            "Welcome glass smoke test failed; see build/welcome-glass-smoke/logs/latest.log"
        }
    }
}

// Same welcome flow, but with a default profile configured: the profile load on the first world
// tick used to run after the guide and put the old glass settings back.
tasks.register<JavaExec>("welcomeProfileSmoke") {
    maxHeapSize = "768m"
    jvmArgs("-XX:ParallelGCThreads=2", "-XX:CICompilerCount=2")
    dependsOn(tasks.testClasses, "downloadAssets")
    mainClass.set("net.minecraft.launchwrapper.Launch")
    classpath = sourceSets.test.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    doFirst {
        var section = ""
        val launchArgs = mutableListOf<String>()
        file(".gradle/loom-cache/launch.cfg").forEachLine { line ->
            if (!line.startsWith("\t")) section = line.trim()
            else if (section == "commonProperties" || section == "clientProperties") {
                val entry = line.trim().split('=', limit = 2)
                if (entry.size == 2) systemProperty(entry[0], entry[1])
            } else if (section == "commonArgs" || section == "clientArgs") launchArgs.add(line.trim())
        }
        systemProperty("neoraven.welcomeSmoke", "true")
        args(launchArgs)
        args("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker", "--version", "1.8.9", "--width", "1280", "--height", "720")
        workingDir = file("build/welcome-profile-smoke")
        workingDir.deleteRecursively()
        workingDir.mkdirs()
        file("build/welcome-profile-smoke/keystrokes").mkdirs()
        // A default profile from an earlier install, saved with glass off, and no setup marker.
        file("build/welcome-profile-smoke/keystrokes/config.txt").writeText(
            "default_profile: default\nlanguage: en\nsave_categories: false\n" +
            "friends: \nenemies: \nonyx_panel_x: 0.0\nonyx_panel_y: 0.0\n"
        )
        file("build/welcome-profile-smoke/keystrokes/profiles").mkdirs()
        file("build/welcome-profile-smoke/keystrokes/profiles/default.json").writeText(
            "{\n  \"keybind\": 0,\n  \"modules\": [\n    {\n      \"name\": \"Onyx ClickGUI\",\n" +
            "      \"enabled\": false,\n      \"hidden\": false,\n      \"keybind\": 54,\n      \"holdBind\": false,\n" +
            "      \"Frosted glass\": false,\n      \"Liquid glass\": false\n    }\n  ]\n}\n"
        )
        // Glass off in the shared preferences as well, so the guide starts from "solid".
        file("build/welcome-profile-smoke/keystrokes/glass-theme.properties").writeText(
            "theme=0\nfrost=0.88\nliquid=0.38\nmatte=0.65\ncolour=false\nmatteEnabled=false\n" +
            "frostEnabled=false\nliquidEnabled=false\naccent=-10070716\n"
        )
        file("build/welcome-profile-smoke/PASS.txt").delete()
        file("build/welcome-profile-smoke/FAIL.txt").delete()
    }
    doLast {
        check(file("build/welcome-profile-smoke/PASS.txt").isFile && !file("build/welcome-profile-smoke/FAIL.txt").exists()) {
            "Welcome/profile smoke test failed; see build/welcome-profile-smoke/logs/latest.log"
        }
    }
}
