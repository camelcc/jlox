plugins {
    kotlin("jvm")
}

group = "com.camelcc"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.squareup:kotlinpoet:1.11.0")
    implementation("com.squareup:kotlinpoet-ksp:1.11.0")
    implementation("com.google.devtools.ksp:symbol-processing-api:1.6.10-1.0.2")
}

sourceSets.main {
    java.srcDirs("src/main/kotlin")
}

