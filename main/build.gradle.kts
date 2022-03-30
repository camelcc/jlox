plugins {
    id("com.google.devtools.ksp")
    kotlin("jvm")
}

group = "com.camelcc"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":ASTBuilder"))
    ksp(project(":ASTBuilder"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}