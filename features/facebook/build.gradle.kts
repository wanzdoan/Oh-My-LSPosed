plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tn.loukious.facebookappadsremover"
    compileSdk = 36
    defaultConfig { minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"1.22\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
    lint { disable += "Instantiatable" }
}
dependencies {
    implementation(project(":core"))
compileOnly("io.github.libxposed:api:102.0.0")
testImplementation("junit:junit:4.13.2")
implementation("androidx.core:core-ktx:1.16.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")
implementation("io.github.libxposed:service:101.0.0")
implementation("org.luckypray:dexkit:2.2.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jsoup:jsoup:1.17.2")
}
