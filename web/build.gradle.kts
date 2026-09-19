import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("rikkahub.android.library")
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources."

    workingDir = webUiDir.asFile
    when {
        Os.isFamily(Os.FAMILY_MAC) -> commandLine("zsh", "-ic", "pnpm run build")
        Os.isFamily(Os.FAMILY_WINDOWS) -> commandLine("cmd", "/c", "pnpm run build")
        else -> commandLine("pnpm", "run", "build")
    }

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)
}

android {
    namespace = "me.rerere.rikkahub.web"

    defaultConfig {
        minSdk = 24
    }
}

/**
 * WebUI 前端构建的触发策略。
 *
 * 历史坑：原先无条件写成 `tasks.named("preBuild") { dependsOn(buildWebUi) }`。
 * `preBuild` 是**所有**构建任务的公共前置，于是连 `:app:testDebugUnitTest`
 * 这种纯 JVM 测试都会先跑一遍整个前端构建。2026-09-19 前端构建连续两次卡死
 * （esbuild 与 node 互相等死，静止 28 / 35 分钟零输出），把测试一起拖住。
 *
 * ⚠️ 第二个坑（2026-09-19 二次排查）：**不能用 `name.startsWith("package")` 判断打包任务**。
 * Android Gradle Plugin 有大量以 `package` 开头的**资源处理**任务，例如
 * `packageDebugResources` —— 它是 `generateDebugResources` 的产物汇总，
 * **任何** compile / test / lint 都会经过它，并不是「出 APK/AAR」。实测：
 * 用它匹配会让 `:web:preBuild` 单独跑时干净，但 `:web:packageDebugResources`、
 * `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 全部重新被拖上 `buildWebUi`，
 * 「测试时不构建前端」静默失效。
 *
 * 因此改为**显式白名单**：只有真正「出包」的任务才依赖前端产物。
 */
val webUiBuilt = webStaticResourcesDir.file("index.html").asFile.exists()

// ① 真正出包的任务：无条件需要前端产物
//    web 模块自身的 assemble/bundle 是次要的（app 才是最终 APK），
//    关键是**其他模块**出包时也要带上前端 —— 否则打出的 APK 里 WebUI 为空。
//    因此用 `tasks.matching` 监听全项目任务名，而不是只听本项目。
val packagingTaskNames = setOf(
    "assembleDebug",
    "assembleRelease",
    "bundleDebugAar",
    "bundleReleaseAar",
)
rootProject.allprojects {
    tasks.matching { it.name in packagingTaskNames }.configureEach {
        // 避免自依赖：buildWebUi 本身不匹配这些名字
        dependsOn(buildWebUi)
    }
}

// ② 首次构建（static 缺失）：编译/测试也要先产出一次
if (!webUiBuilt) {
    logger.lifecycle(
        "[web] static/ 无产物（首次 clone 或已清理），编译前将先构建 WebUI 前端。"
    )
    tasks.matching { it.name.startsWith("preBuild") }.configureEach {
        dependsOn(buildWebUi)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
