import com.avito.instrumentation.configuration.InstrumentationPluginConfiguration.GradleInstrumentationPluginConfiguration
import com.avito.instrumentation.configuration.target.scheduling.SchedulingConfiguration
import com.avito.instrumentation.configuration.target.scheduling.quota.QuotaConfiguration
import com.avito.instrumentation.configuration.target.scheduling.reservation.TestsBasedDevicesReservationConfiguration
import com.avito.instrumentation.reservation.request.Device.Emulator.Emulator22
import com.avito.instrumentation.reservation.request.Device.Emulator.Emulator27
import com.avito.kotlin.dsl.getOptionalStringProperty

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.avito.android.instrumentation-tests")
}

val kotlinVersion: String by project
val androidXVersion: String by project
val sentryVersion: String by project
val truthVersion: String by project
val okhttpVersion: String by project
val junitVersion: String by project

//todo cleaner way to get these properties
val buildTools = requireNotNull(project.properties["buildToolsVersion"]).toString()
val compileSdk = requireNotNull(project.properties["compileSdkVersion"]).toString().toInt()
val targetSdk = requireNotNull(project.properties["targetSdkVersion"]).toString()
val minSdk = requireNotNull(project.properties["minSdkVersion"]).toString()

android {
    buildToolsVersion(buildTools)
    compileSdkVersion(compileSdk)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    defaultConfig {
        minSdkVersion(minSdk)
        targetSdkVersion(targetSdk)

        versionName = "1.0"
        versionCode = 1
        testInstrumentationRunner = "com.avito.android.ui.test.TestAppRunner"

        testInstrumentationRunnerArguments(
            mapOf(
                "planSlug" to "AndroidTestApp",
                "unnecessaryUrl" to "https://localhost"
            )
        )
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-maps:17.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("androidx.appcompat:appcompat:$androidXVersion")
    implementation("androidx.recyclerview:recyclerview:$androidXVersion")
    implementation("com.google.android.material:material:$androidXVersion")

    androidTestImplementation("junit:junit:$junitVersion")
    androidTestImplementation(project(":test-inhouse-runner")) { isTransitive = false }
    androidTestImplementation(project(":test-annotations")) { isTransitive = false }
    androidTestImplementation(project(":test-report")) { isTransitive = false }
    androidTestImplementation(project(":report-viewer")) { isTransitive = false }
    androidTestImplementation(project(":junit-utils")) { isTransitive = false }
    androidTestImplementation(project(":ui-testing-core"))
    androidTestImplementation("io.sentry:sentry:$sentryVersion")
    androidTestImplementation("com.google.truth:truth:$truthVersion")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:$okhttpVersion")
}

tasks.getByName("build").dependsOn("$path:instrumentationUi")

extensions.getByType<GradleInstrumentationPluginConfiguration>().apply {

    //todo make these params optional features in plugin
    reportApiUrl = project.getOptionalStringProperty("avito.report.url") ?: "http://stub"
    reportApiFallbackUrl = project.getOptionalStringProperty("avito.report.fallbackUrl") ?: "http://stub"
    reportViewerUrl = project.getOptionalStringProperty("avito.report.viewerUrl") ?: "http://stub"
    registry = project.getOptionalStringProperty("avito.registry") ?: "registry"
    sentryDsn = project.getOptionalStringProperty("avito.instrumentaion.sentry.dsn") ?: "stub"
    slackToken = project.getOptionalStringProperty("avito.slack.token") ?: "stub"
    fileStorageUrl = project.getOptionalStringProperty("avito.fileStorage.url") ?: "http://stub"

    output = project.rootProject.file("outputs/${project.name}/instrumentation").path

    logcatTags = setOf(
        "UITestRunner:*",
        "ActivityManager:*",
        "ReportTestListener:*",
        "StorageJsonTransport:*",
        "TestReport:*",
        "VideoCaptureListener:*",
        "TestRunner:*",
        "SystemDialogsManager:*",
        "ito.android.de:*", //по этому тэгу система пишет логи об использовании hidden/restricted api https://developer.android.com/distribute/best-practices/develop/restrictions-non-sdk-interfaces
        "*:E"
    )

    instrumentationParams = mapOf(
        "videoRecording" to "failed",
        "jobSlug" to "FunctionalTests"
    )

    configurationsContainer.register("ui") {
        tryToReRunOnTargetBranch = false
        reportSkippedTests = true
        rerunFailedTests = true
        reportFlakyTests = true

        targetsContainer.register("api22") {
            deviceName = "API22"

            scheduling = SchedulingConfiguration().apply {
                quota = QuotaConfiguration().apply {
                    retryCount = 1
                    minimumSuccessCount = 1
                }

                reservation = TestsBasedDevicesReservationConfiguration().apply {
                    device = Emulator22
                    maximum = 50
                    minimum = 2
                    testsPerEmulator = 3
                }
            }
        }

        targetsContainer.register("api27") {
            deviceName = "API27"

            scheduling = SchedulingConfiguration().apply {
                quota = QuotaConfiguration().apply {
                    retryCount = 1
                    minimumSuccessCount = 1
                }

                reservation = TestsBasedDevicesReservationConfiguration().apply {
                    device = Emulator27
                    maximum = 50
                    minimum = 2
                    testsPerEmulator = 3
                }
            }
        }
    }
}

configurations.all {
    if (name.contains("AndroidTestRuntimeClasspath")) {
        resolutionStrategy {
            force("org.jetbrains:annotations:16.0.1")
        }
    }
}
