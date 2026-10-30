plugins { id("com.android.application") }
android {
    namespace = "dev.ohmy.lsposed"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.ohmy.lsposed"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "1.3.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}
dependencies {
    implementation(project(":core"))
    listOf("zalo", "locket", "tiktok", "facebook", "messenger", "chrome").forEach {
        implementation(project(":features:$it"))
    }
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.github.libxposed:service:101.0.0")
    testImplementation("junit:junit:4.13.2")
}
