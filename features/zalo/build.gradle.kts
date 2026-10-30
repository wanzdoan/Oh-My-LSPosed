plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.betterzl.module"
    compileSdk = 36
    defaultConfig { minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"1.3.0-zalo-settings\"")
        buildConfigField("int", "VERSION_CODE", "4")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
    lint { disable += "Instantiatable" }
}
dependencies {
    implementation("org.luckypray:dexkit:2.2.0")
    implementation(project(":core"))
compileOnly("io.github.libxposed:api:102.0.0")
testImplementation("junit:junit:4.13.2")
}
