plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "io.vulscan"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    // vulscan-kit: локально (publishToMavenLocal) или из GitHub Packages, если заданы gpr.user / gpr.key
    mavenLocal()
    mavenCentral()
    val gprUser = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR"))
    val gprKey = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN"))
    if (gprUser.isPresent && gprKey.isPresent) {
        maven {
            url = uri("https://maven.pkg.github.com/tolegenjob/vulscan-kit")
            credentials {
                username = gprUser.get()
                password = gprKey.get()
            }
        }
    }
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    // Только contracts: перечисления (ModuleType/VulnType/Verdict/Severity) + Json(ObjectMapper) —
    // инструменту оценки не нужны ни net (SafeHttpClient), ни worker (Kafka).
    implementation("io.vulscan:contracts:${property("kitVersion")}")
    implementation(libs.snakeyaml)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("io.vulscan.eval.EvalMainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
