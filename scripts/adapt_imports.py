"""Apply the audited integration changes immediately after import_sources.py."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def edit(path, transform):
    p = ROOT / path
    p.write_text(transform(p.read_text(encoding='utf-8')), encoding='utf-8')

modern = {
    'features/tiktok/src/main/java/com/golda/patchertiktok/ModuleMain.java': ('tiktok', ['com.zhiliaoapp.musically','com.ss.android.ugc.trill']),
    'features/facebook/src/main/java/tn/loukious/facebookappadsremover/ModuleMain.kt': ('facebook', ['com.facebook.katana']),
    'features/messenger/src/main/java/com/aitiniu/orcapeek/hook/OrcaPeekModule.java': ('messenger', ['com.facebook.orca']),
    'features/chrome/src/main/java/dev/alastorkaneki/chromescript/ModuleMain.java': ('chrome', ['com.android.chrome']),
}
for path, (name, targets) in modern.items():
    def adapt(text):
        text = text.replace('import io.github.libxposed.api.XposedModule', 'import dev.ohmy.core.GatedModule')
        arguments = ', '.join('"'+s+'"' for s in [name]+targets)
        if path.endswith('.kt'):
            text = text.replace(': XposedModule()', f': GatedModule({arguments})')
        else:
            import re
            text = text.replace('extends XposedModule', 'extends GatedModule')
            classname = re.search(r'public final class (\w+)', text).group(1)
            text = text.replace('extends GatedModule {', f'extends GatedModule {{\n    public {classname}() {{ super({arguments}); }}')
        for old,new in [('onModuleLoaded','featureLoaded'),('onPackageLoaded','featurePackageLoaded'),('onPackageReady','featureReady')]:
            text = text.replace('void '+old+'(', 'void '+new+'(').replace('fun '+old+'(', 'fun '+new+'(')
        return text
    edit(path, adapt)

# Only the modern TikTok entry is packaged. Its feature code already has a modern backend.
(ROOT/'features/tiktok/src/main/java/com/golda/patchertiktok/MainHook.java').unlink()

for name in ['zalo','locket']:
    for p in (ROOT/f'features/{name}/src/main/java').rglob('*'):
        if p.suffix in ['.java','.kt']:
            p.write_text(p.read_text(encoding='utf-8').replace('de.robv.android.xposed.', 'dev.ohmy.core.compat.'), encoding='utf-8')

edit('features/locket/src/main/java/com/locket/gold/HookMain.java', lambda s: s.replace('import dev.ohmy.core.compat.IXposedHookLoadPackage;\n','').replace('import dev.ohmy.core.compat.callbacks.XC_LoadPackage.LoadPackageParam;\n','').replace('implements IXposedHookLoadPackage','').replace('    @Override\n    public void handleLoadPackage(final LoadPackageParam lpparam) {','    public void install(String packageName, String processName, ClassLoader classLoader) {').replace('lpparam.packageName','packageName').replace('lpparam.processName','processName').replace('lpparam.classLoader','classLoader'))

def zalo(text):
    text = '\n'.join(line for line in text.splitlines() if not line.startswith('import com.highcapable.') and not line.startswith('@InjectYuki'))
    text = text.replace('object HookEntry : IYukiHookXposedInit {', 'object HookEntry {')
    start = text.index('    override fun onInit()')
    end = text.index('    /**', start)
    text = text[:start] + '''    fun install(loader: ClassLoader) {
        with(HookSession(loader)) {
            YLog.info(msg = "[$TAG] Initializing BetterZalo hooks")
            hookAntiRecallAdaptive()
            hookCustomRingtoneAdaptive()
            hookStealthSeenAndTypingAdaptive()
            FloatingBarHooks.install(loader)
        }
    }

''' + text[end:]
    return text.replace('com.highcapable.yukihookapi.hook.param.PackageParam.', 'HookSession.')
edit('features/zalo/src/main/java/com/betterzl/module/hook/HookEntry.kt', zalo)
(ROOT/'features/zalo/src/main/java/com/betterzl/module/ui/MainActivity.kt').unlink()

# Broadcast destinations must be the new containing APK, while action names remain namespaced.
edit('features/facebook/src/main/java/tn/loukious/facebookappadsremover/core/SessionBackup.kt', lambda s: s.replace('.setPackage("tn.loukious.facebookappadsremover")', '.setPackage(dev.ohmy.core.ModuleIds.APP_ID)'))
# In the combined app this setting controls its launcher alias.
edit('features/facebook/src/main/java/tn/loukious/facebookappadsremover/ui/LauncherIcon.kt', lambda s: s.replace('private const val ALIAS = "tn.loukious.facebookappadsremover.ui.LauncherAlias"','private const val ALIAS = "dev.ohmy.lsposed.LauncherAlias"'))

# Retain the complete independent Facebook settings, in a white theme.
edit('features/facebook/src/main/java/tn/loukious/facebookappadsremover/ui/MainActivity.kt', lambda s: s.replace('DynamicColors.applyToActivityIfAvailable(this)', '// Unified app uses a fixed white palette.'))
edit('features/facebook/src/main/res/values/themes.xml', lambda s: s.replace('Theme.Material3.DayNight','Theme.Material3.Light').replace('ThemeOverlay.Material3.DynamicColors.DayNight','ThemeOverlay.Material3.Light'))
night = ROOT/'features/facebook/src/main/res/values-night/themes.xml'
if night.exists(): night.unlink()
