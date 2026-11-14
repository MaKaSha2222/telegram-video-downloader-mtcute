# Bot API Server native build နှင့် progress bridge

မူရင်း ZIP မှရရှိခဲ့သော `liblocalbotapi.so` နှင့် `libc++_shared.so` ကို `android/prebuilt/arm64-v8a/` ထဲတွင် reference အဖြစ် ထိန်းသိမ်းထားသည်။ ၎င်းတို့သည် ARM64 Android ELF library များဖြစ်သော်လည်း `liblocalbotapi.so` က download-progress JNI method များ မထုတ်ပေးသောကြောင့် APK ထဲသို့ ထည့်မသုံးပါ။

ယခု app သည် `android/app/src/main/cpp/CMakeLists.txt` နှင့် `vendor/telegram-bot-api/` ထဲရှိ source ကို အသုံးပြုပြီး `liblocalbotapi.so` ကို Android NDK/CMake ဖြင့် ပြန်တည်ဆောက်သည်။ C++ shim သည် `expo.modules.localbotapi.LocalBotApiModule` JNI class နဲ့ ကိုက်ညီသည်။ TDLib `updateFile` မှ ရရှိသော `downloaded_size`, `size`, `expected_size` နှင့် download ပြီး/မပြီး အခြေအနေကို native thread-safe registry ထဲသိမ်းပြီး Kotlin က Bot API `file_id` တစ်ခုချင်းစီအလိုက် 500 ms ခြားဖတ်သည်။

ဖိုင်အရွယ်အစားကို အတိအကျမသိပါက `expected_size` ကို ခန့်မှန်း denominator အဖြစ် အသုံးပြုသည်။ Exact `size` မရှိဘဲ `expected_size` သာရှိသည့်အချိန်တွင် download ပြီးကြောင်း TDLib က မပြမချင်း UI ရာခိုင်နှုန်းကို 99% ထက်မကျော်စေပါ။ အရွယ်အစားနှစ်ခုလုံးမရနိုင်လျှင် bytes နှင့် MB/s ကိုပြပြီး percentage ကို indeterminate အဖြစ်ထားသည်။ Download တစ်ခုချင်းစီ၏ progress ကို မတူညီသော Bot API `file_id` key ဖြင့် ခွဲထားသည်။

Android SDK, NDK, CMake 3.22.1 နှင့် JDK 17 ပါသော environment လိုအပ်သည်။ ဒီ sandbox မှာ Android build tools မရှိသဖြင့် native CMake build၊ APK build နှင့် ARM64 device end-to-end test မလုပ်နိုင်ခဲ့ပါ။ Build အောင်မြင်ပြီး device ပေါ်တွင် စမ်းသပ်မပြီးမချင်း production-ready ဟု မယူဆပါနှင့်။
