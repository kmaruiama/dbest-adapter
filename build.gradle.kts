plugins {
    kotlin("jvm") version "2.0.21"
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dbest.kernel.http.ServerKt")
}

sourceSets {
    main { kotlin.setSrcDirs(listOf("src/features", "src/kernel")) }
    test { kotlin.setSrcDirs(listOf("test")) }
}

repositories {
    mavenCentral()
}

val dbestHome = file(System.getenv("DBEST_HOME") ?: providers.gradleProperty("dbest.home").orNull ?: "../DBest")

dependencies {
    implementation(files(dbestHome.resolve("target/classes")))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.http4k:http4k-core:5.47.0.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val compileIbd = tasks.register<Exec>("compileIbd") {
    workingDir = dbestHome
    commandLine("mvn", "-q", "-DskipTests", "compile")
    onlyIf { !dbestHome.resolve("target/classes/ibd").exists() }
}

tasks.compileKotlin {
    dependsOn(compileIbd)
}
