plugins { id("com.android.library") }
android {
    namespace = "com.aitiniu.orcapeek"
    compileSdk = 36
    defaultConfig { minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"0.10.4\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    
    lint { disable += "Instantiatable" }
}
dependencies {
    implementation(project(":core"))
compileOnly("io.github.libxposed:api:102.0.0")
testImplementation("junit:junit:4.13.2")
implementation("io.github.libxposed:service:101.0.0")
}
