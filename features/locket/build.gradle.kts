plugins { id("com.android.library") }
android {
    namespace = "com.locket.gold"
    compileSdk = 36
    defaultConfig { minSdk = 28
        buildConfigField("String", "VERSION_NAME", "\"2.1.0\"")
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
}
