// ====================================================================================================
//   UNOFFICIAL LINUX (POP!_OS) PORT -- THIS IS NOT THE ORIGINAL PROJECT
//
//   ChairPlay is written by Oleksiy Strelets (NumberOneBot), GPL-3.0:
//       https://github.com/NumberOneBot/ChairPlay
//   This file was ADDED by an unofficial port of ChairPlay to Pop!_OS / Ubuntu Linux, made for
//   personal use only. The port is not affiliated with or endorsed by the ChairPlay author, DiPlay,
//   xcertplay, or Apple. Please do not report problems with this port to the original project.
//   See LINUX-PORT-NOTICE.md.
// ====================================================================================================

// What ships on Linux: the notices in the jar and the distribution, the start scripts, a self-contained app image
// that jpackage makes, and a .deb of it that installs into /opt/chairplay with a menu entry. Applied by
// build.gradle.kts in place of gradle/package.gradle.kts and gradle/installer.gradle.kts on Linux, after
// gradle/native.gradle.kts and gradle/web.gradle.kts, whose libraries it copies into the image.
//
// The .deb needs dpkg-deb and fakeroot (`sudo apt install fakeroot`); the app image needs neither. The app keeps
// its data in ~/.local/share/ChairPlay (LinuxDesktop.dataHome), so the program folder stays read-only.

interface FileOps {
    @get:Inject val fs: FileSystemOperations
}

@Suppress("UNCHECKED_CAST")
val socketHandleOpens = project.extra["socketHandleOpens"] as List<String>
// The release version (chairplay.version, X.Y.Z), set in build.gradle.kts; null in a development build.
val appVersion = project.extra["appVersion"] as String?
val javaToolchains = extensions.getByType<JavaToolchainService>()

// Concentus and SpeexDSP are BSD-licensed: their notices travel with the binaries, as on Windows.
val concentusLicence = layout.buildDirectory.file("upstream-core/org/concentus/LICENSE")
val speexLicence = layout.projectDirectory.file("upstream/shared/src/main/jni/speexdsp/COPYING")

tasks.named<Jar>("jar") {
    from(concentusLicence) {
        rename { "LICENSE-concentus" }
        into("META-INF")
    }
}

the<DistributionContainer>()["main"].contents {
    from(concentusLicence) { rename { "LICENSE-concentus" } }
    from(speexLicence) { rename { "LICENSE-speexdsp" } }
}

// The start scripts (installDist, `./build/install/chairplay/bin/chairplay run`) load the .so files from bin/.
tasks.named<CreateStartScripts>("startScripts") {
    defaultJvmOpts = socketHandleOpens + "-Djava.library.path=__APP_HOME__/bin"
    doLast {
        unixScript.writeText(unixScript.readText().replace("__APP_HOME__", "'\"\$APP_HOME\"'"))
        windowsScript.writeText(windowsScript.readText().replace("__APP_HOME__/bin", "%APP_HOME%\\bin"))
    }
}

// As on Windows, less jdk.crypto.mscapi, which Linux' JDK does not have.
val appImageModules = listOf(
    "java.base", "java.desktop", "java.logging", "java.management", "java.naming", "java.sql", "java.xml",
    "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.httpserver",
)

val jpackageInput = layout.buildDirectory.dir("jpackage-input")
val jpackageOut = layout.buildDirectory.dir("jpackage")
val packageVersion = appVersion ?: "0.0.0"

// jpackage puts --input into the image's lib/app, which the launcher's java.library.path ($APPDIR) names, so the
// .so files sit there beside the jars; so do the notices, which the About page opens.
val stageJpackageInput by tasks.registering(Sync::class) {
    dependsOn("installDist")
    from(layout.buildDirectory.dir("install/chairplay/lib"))
    from(layout.buildDirectory.dir("install/chairplay/bin")) { include("*.so") }
    from(layout.buildDirectory.dir("install/chairplay")) { include("LICENSE-*") }
    from(layout.projectDirectory.file("LICENSE"))
    from(layout.projectDirectory.file("THIRD_PARTY_NOTICES"))
    from(layout.projectDirectory.dir("upstream/docs/licenses/dependencies")) { into("licenses") }
    from(layout.projectDirectory.dir("notices")) { into("licenses") }
    into(jpackageInput)
}

fun jpackageCommand(vararg extra: String): List<String> {
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(17) }
    return listOf(launcher.get().metadata.installationPath.file("bin/jpackage").asFile.path) + extra.toList()
}

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/jpackage/ChairPlay, a self-contained app image with the launcher bin/ChairPlay."
    dependsOn(stageJpackageInput)
    val icon = layout.projectDirectory.file("packaging/icon/chairplay-1024.png")
    val ops = objects.newInstance<FileOps>()
    doFirst {
        ops.fs.delete { delete(jpackageOut.get().dir("ChairPlay")) }
        commandLine(
            jpackageCommand(
                "--type", "app-image",
                "--name", "ChairPlay",
                "--app-version", packageVersion,
                "--vendor", "Oleksiy Strelets",
                "--input", jpackageInput.get().asFile.path,
                "--main-jar", "chairplay.jar",
                "--main-class", "io.github.numberonebot.chairplay.MainKt",
                // Arguments given to the launcher replace `run`, so `devices` and `probe-bt` work from a terminal.
                "--arguments", "run",
                "--icon", icon.asFile.path,
                "--add-modules", appImageModules.joinToString(","),
                "--dest", jpackageOut.get().asFile.path,
                "--java-options", "-Djava.library.path=\$APPDIR",
                *socketHandleOpens.flatMap { listOf("--java-options", it) }.toTypedArray(),
            ),
        )
    }
}

tasks.register<Exec>("linuxDeb") {
    group = "distribution"
    description = "Builds build/jpackage/chairplay_<version>_<arch>.deb from the app image (needs dpkg-deb and fakeroot)."
    dependsOn(jpackageImage)
    val icon = layout.projectDirectory.file("packaging/icon/chairplay-1024.png")
    doFirst {
        commandLine(
            jpackageCommand(
                "--type", "deb",
                "--app-image", jpackageOut.get().dir("ChairPlay").asFile.path,
                "--name", "ChairPlay",
                "--app-version", packageVersion,
                "--vendor", "Oleksiy Strelets",
                "--description", "Wireless Apple CarPlay on your Linux PC (unofficial Linux port of ChairPlay by Oleksiy Strelets)",
                "--icon", icon.asFile.path,
                "--linux-package-name", "chairplay",
                "--linux-deb-maintainer", "unofficial-linux-port@invalid",
                "--linux-menu-group", "AudioVideo",
                "--linux-app-category", "video",
                "--linux-shortcut",
                // BlueZ for the iPhone's Bluetooth, NetworkManager's nmcli for the Wi-Fi, and xdg-open.
                "--linux-package-deps", "bluez, xdg-utils",
                "--license-file", layout.projectDirectory.file("LICENSE").asFile.path,
                "--dest", jpackageOut.get().asFile.path,
            ),
        )
    }
}

// Runs the app image from the build, for trying it without installing the .deb.
tasks.register<Exec>("runImage") {
    group = "application"
    description = "Starts build/jpackage/ChairPlay/bin/ChairPlay."
    dependsOn(jpackageImage)
    commandLine(jpackageOut.get().file("ChairPlay/bin/ChairPlay").asFile.path)
}
