import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

plugins {
  java
  application
  id("com.gradleup.shadow") version "9.2.2"
}

group = "ssonin"
version = "1.0.0-SNAPSHOT"

repositories {
  mavenCentral()
}

val assertjVersion = "3.27.3"
val caffeineVersion = "3.2.3"
val junitJupiterVersion = "5.9.1"
val logbackVersion = "1.5.32"
val slf4jVersion = "2.0.17"
val vertxVersion = "5.0.8"

val mainVerticleName = "ssonin.ccmemcached.App"
val launcherClassName = "io.vertx.launcher.application.VertxApplication"

application {
  mainClass.set(launcherClassName)
}

dependencies {
  implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))

  implementation("ch.qos.logback:logback-classic:${logbackVersion}")
  implementation("com.github.ben-manes.caffeine:caffeine:${caffeineVersion}")
  implementation("io.vertx:vertx-core")
  implementation("io.vertx:vertx-launcher-application")
  implementation("org.slf4j:slf4j-api:$slf4jVersion")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testImplementation("org.assertj:assertj-core:${assertjVersion}")
  testImplementation("io.vertx:vertx-junit5")
  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<ShadowJar> {
  archiveClassifier.set("fat")
  manifest {
    attributes(mapOf("Main-Verticle" to mainVerticleName))
  }
  mergeServiceFiles()
}

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(PASSED, SKIPPED, FAILED)
  }
}

tasks.withType<JavaExec> {
  args = listOf(mainVerticleName)
}
