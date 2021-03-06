plugins {
    id("kotlin")
    `maven-publish`
}

val kotlinVersion: String by project
val androidGradlePluginVersion: String by project
val funktionaleVersion: String by project

dependencies {
    api("com.android.tools.build:gradle:$androidGradlePluginVersion")

    implementation(gradleApi())
    implementation(project(":files"))
    implementation(project(":process"))
    implementation(project(":kotlin-dsl-support"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.funktionale:funktionale-try:$funktionaleVersion")

    testImplementation(testFixtures(project(":process")))
}
