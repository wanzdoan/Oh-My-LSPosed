"""One-time source snapshot. Run only when intentionally replacing imported sources."""
from pathlib import Path
import shutil
import json

ROOT = Path(__file__).resolve().parents[1]
SOURCES = {
    'zalo': ('H:/Projects/BetterZl/BetterZaloModule', 'com.betterzl.module', '1.1.3-glass'),
    'locket': ('H:/Projects/LocketGoldLSPosed', 'com.locket.gold', '2.1.0'),
    'tiktok': ('H:/Projects/TikTok/TiktokPatchXposed-Project/TiktokPatchXposed-Gradle', 'com.golda.patchertiktok', '4.4.0'),
    'facebook': ('H:/Projects/tn.loukious.facebookappadsremover', 'tn.loukious.facebookappadsremover', '1.22'),
    'messenger': ('D:/projects/01-Android/KernelSUNextModules/Messenger/orcapeek', 'com.aitiniu.orcapeek', '0.10.4'),
    'chrome': ('D:/projects/01-Android/RootModules/ChromeScript/ChromeScriptModule', 'dev.alastorkaneki.chromescript', '0.6.1'),
}

def write(path, content):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')

for name, (source, namespace, version) in SOURCES.items():
    source = Path(source)
    dest = ROOT / 'features' / name
    if dest.exists():
        raise SystemExit(f'Refusing to overwrite {dest}')
    for folder in ['java', 'res', 'assets', 'jniLibs']:
        if (source / 'app/src/main' / folder).exists():
            shutil.copytree(source / 'app/src/main' / folder, dest / 'src/main' / folder)
    if (dest / 'src/main/assets/xposed_init').exists():
        (dest / 'src/main/assets/xposed_init').unlink()
    if (source / 'app/src/test').exists():
        shutil.copytree(source / 'app/src/test', dest / 'src/test')
    for pattern in ['LICENSE*', 'NOTICE*', 'COPYING*']:
        for f in source.glob(pattern):
            if f.is_file():
                (dest / 'upstream').mkdir(parents=True, exist_ok=True)
                shutil.copy2(f, dest / 'upstream' / f.name)
    write(f'features/{name}/upstream/source.json', json.dumps({'source': str(source), 'namespace': namespace, 'version': version}, indent=2))
    plugins = 'plugins { id("com.android.library")' + ('; id("org.jetbrains.kotlin.android")' if name in ['zalo','facebook'] else '') + ' }'
    dependencies = ['implementation(project(":core"))', 'compileOnly("io.github.libxposed:api:102.0.0")', 'testImplementation("junit:junit:4.13.2")']
    if name == 'facebook':
        dependencies += ['implementation("androidx.core:core-ktx:1.16.0")', 'implementation("androidx.appcompat:appcompat:1.7.0")', 'implementation("com.google.android.material:material:1.12.0")', 'implementation("io.github.libxposed:service:101.0.0")', 'implementation("org.luckypray:dexkit:2.2.0")', 'implementation("com.squareup.okhttp3:okhttp:4.12.0")', 'implementation("org.jsoup:jsoup:1.17.2")']
    if name == 'messenger':
        dependencies += ['implementation("io.github.libxposed:service:101.0.0")']
    fields = f'buildConfigField("String", "VERSION_NAME", "\\\"{version}\\\"")\n        buildConfigField("int", "VERSION_CODE", "1")'
    write(f'features/{name}/build.gradle.kts', f'''{plugins}
android {{
    namespace = "{namespace}"
    compileSdk = 36
    defaultConfig {{ minSdk = 28
        {fields}
    }}
    buildFeatures {{ buildConfig = true }}
    compileOptions {{ sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }}
    {('kotlinOptions { jvmTarget = "21" }' if name in ['zalo','facebook'] else '')}
    lint {{ disable += "Instantiatable" }}
}}
dependencies {{
    {chr(10).join(dependencies)}
}}
''')
    write(f'features/{name}/src/main/AndroidManifest.xml', '<manifest xmlns:android="http://schemas.android.com/apk/res/android" />')

base = Path(SOURCES['facebook'][0])
for f in ['gradlew','gradlew.bat']:
    shutil.copy2(base/f, ROOT/f)
shutil.copytree(base/'gradle', ROOT/'gradle', dirs_exist_ok=True)
