# Bot API Server native binary ပါဝင်မှု

`android/app/src/main/jniLibs/arm64-v8a/` ဖိုလ်ဒါထဲတွင် မူရင်း ZIP ရှိ `expo.modules.localbotapi-1.0.0.aar` ဖိုင်မှ ထုတ်ယူထားသော `liblocalbotapi.so` နှင့် ၎င်းနှင့်တွဲဖက်အသုံးပြုရသော `libc++_shared.so` တို့ ပါဝင်သည်။ ဖိုင်နှစ်ခုလုံးကို အသစ်ပြန်တည်ဆောက်ခြင်းမပြုဘဲ မူရင်း AAR မှ တိုက်ရိုက်ထုတ်ယူထားသည်။ ၎င်းတို့သည် Android `arm64-v8a` ELF shared library များဖြစ်ကြောင်းနှင့် SHA-256 တန်ဖိုးများကို စစ်ဆေးထားသည်။

`liblocalbotapi.so` ၏ JNI သင်္ကေတများသည် `expo.modules.localbotapi.LocalBotApiModule` package/class အမည်ကို အသုံးပြုထားသည်။ ထိုအမည်နှင့် native method များ ကိုက်ညီစေရန် Expo dependency မလိုအပ်သော Kotlin compatibility class ကို `android/app/src/main/java/expo/modules/localbotapi/LocalBotApiModule.kt` တွင် ထည့်ထားသည်။ ပုံမှန် Gradle build သည် `jniLibs` အတွင်းရှိ binary များကို APK ထဲသို့ ထည့်သွင်းပြီး CMake/NDK ဖြင့် server ကို ပြန်မတည်ဆောက်ပါ။ C++ source ကိုလည်း project ထဲတွင် ထိန်းသိမ်းထားသည်။

**မှတ်ချက်:** ပါဝင်သော binary ၏ `nativeStart` method သည် server စတင်နိုင်ခြင်း သို့မဟုတ် မစတင်နိုင်ခြင်းကို `Boolean` တန်ဖိုးဖြင့်သာ ပြန်ပေးသည်။ ထို့ကြောင့် server စတင်မှု မအောင်မြင်ပါက app က error type နှင့် ရရှိနိုင်သော message ကို ပြနိုင်သော်လည်း C++ အတွင်းရှိ exception message အပြည့်အစုံကို ဤ binary မှ ရယူ၍မရပါ။

ဤ binary ကို Android SDK ပါသော build ပတ်ဝန်းကျင် သို့မဟုတ် `arm64-v8a` ဖုန်းပေါ်တွင် လက်တွေ့စမ်းသပ်ထားခြင်း မရှိသေးပါ။ `arm64-v8a` မဟုတ်သော ABI များတွင် ဤ binary ကို အသုံးပြု၍မရပါ။
