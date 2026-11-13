# Android ARM64 app ထဲမှာ Bot API Server run လုပ်နိုင်မလား

## စစ်ဆေးရလဒ်

Telegram Bot API Server ရဲ့ တရားဝင် build လမ်းညွှန်မှာ Windows, Linux, macOS နဲ့ BSD ကို ဖော်ပြထားပြီး Android မပါဝင်ပါ။ [1] Telegram ရဲ့ GitHub ဆွေးနွေးခန်းမှာ developer တစ်ယောက် Android NDK သုံးပြီး `arm64-v8a` အတွက် build အောင်မြင်ခဲ့တယ်လို့ ရေးထားပါတယ်။ ဒါပေမဲ့ အစောပိုင်းမှာ compile error တွေဖြစ်ခဲ့ပြီး၊ repository ကို ကူညီထိန်းသိမ်းသူတစ်ယောက်က Termux မှာ ပြဿနာတွေရှိနိုင်တယ်လို့ သတိပေးထားပါတယ်။ ဒါကြောင့် နည်းပညာအရ စမ်းကြည့်နိုင်ပေမဲ့ Android ကို တရားဝင်ထောက်ပံ့ထားတဲ့ နည်းလမ်း၊ အလုပ်လုပ်မယ်လို့ အာမခံနိုင်တဲ့နည်းလမ်းတော့ မဟုတ်ပါ။ [2]

Bot API Server ကိုယ်တိုင်က C++ နဲ့ရေးထားတာဖြစ်လို့ server အဓိကအစိတ်အပိုင်းကို Kotlin နဲ့ အစားထိုးလို့မရပါ။ Android NDK က C/C++ native code ကို app ထဲထည့်ပေးနိုင်ပြီး Kotlin က JNI နဲ့ ခေါ်သုံးနိုင်ပါတယ်။ ဒါပေမဲ့ server ရဲ့ `main()` ကို APK ထဲကူးထည့်ရုံနဲ့ အလုပ်မလုပ်ပါ။ CMake နဲ့ TDLib ကို ARM64 အတွက် တည်ဆောက်ပြီး၊ Kotlin ကနေ server ကို စတင်၊ ရပ်တန့်၊ ဖိုင်တွေကို စီမံနိုင်အောင် native ချိတ်ဆက်မှု လုပ်ရပါမယ်။ [3]

ဖိုင်ကြီးတွေ download လုပ်နေချိန် app နောက်ခံရောက်သွားရင် ဆက်လုပ်နိုင်ဖို့ Android foreground service နဲ့ notification လိုပါတယ်။ Android 12 နဲ့အထက်မှာ app နောက်ခံမှာရှိနေစဉ် foreground service စတင်တာကို ကန့်သတ်ထားပါတယ်။ ဒါကြောင့် app ကိုဖွင့်ထားတဲ့အချိန် download ကိုစပြီး notification ပြသထားဖို့ လိုပါတယ်။ [4]

## လက်ရှိ project ရဲ့ ကန့်သတ်ချက်

ဒီ project က Expo / React Native / TypeScript starter ဖြစ်ပါတယ်။ Expo မှာ Kotlin native module ထည့်လို့ရပေမဲ့ custom native build လုပ်ရပြီး Expo Go တစ်ခုတည်းနဲ့ စမ်းလို့မရပါ။ App ရဲ့ မျက်နှာပြင်က React Native အဖြစ်ပဲ ကျန်နေပါမယ်။ ဒါကြောင့် လက်ရှိ project ကို app တစ်ခုလုံး Kotlin ဖြစ်တဲ့ Android app အဖြစ် ပြောင်းလို့မရပါ။ Kotlin သီးသန့် app လုပ်ဖို့ Android Studio project အသစ်နဲ့ သီးခြား build ပတ်ဝန်းကျင် လိုပါတယ်။ [5]

## အနှစ်ချုပ်နဲ့ ရွေးချယ်စရာ

ပြင်ပ server မသုံးဘဲ Bot API Server ကို Android ARM64 app ထဲမှာ run လုပ်ဖို့ စမ်းနိုင်ပါတယ်။ ဒါပေမဲ့ Android ကို တရားဝင်ထောက်ပံ့မထားပါဘူး။ C++/NDK/JNI ချိတ်ဆက်မှုလုပ်ရမယ်၊ Android ဖုန်းပေါ်မှာ အလုပ်လုပ်ကြောင်းလည်း မစမ်းရသေးပါ။ အခုအချိန်မှာ APK အောင်မြင်မယ်လို့ ကတိမပေးနိုင်ပါ။

Code မစခင် အောက်ကနှစ်ခုထဲက တစ်ခုရွေးပေးပါ။

- **လက်ရှိ project ကို ဆက်သုံးရန်:** Expo / React Native မျက်နှာပြင်ကိုထားပြီး Kotlin နဲ့ C++ native module ထည့်မယ်။ App တစ်ခုလုံး Kotlin မဖြစ်ပါ။
- **Kotlin သီးသန့် app လုပ်ရန်:** Android Studio project အသစ်ကို သီးခြားတည်ဆောက်ရန် လိုအပ်ပါတယ်။

ဘယ်နည်းလမ်းကိုရွေးရွေး Bot API Server ရဲ့ အဓိကအစိတ်အပိုင်းက C++ ပဲ ဖြစ်နေပါမယ်။

## ကိုးကားထားသော ရင်းမြစ်များ

[1]: https://tdlib.github.io/telegram-bot-api/build.html "Telegram Bot API Server build instructions"
[2]: https://github.com/tdlib/telegram-bot-api/issues/180 "Telegram Bot API Server Android NDK ARM64 build discussion"
[3]: https://developer.android.com/studio/projects/add-native-code "Add C and C++ code to an Android project"
[4]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start "Restrictions on starting a foreground service from the background"
[5]: https://docs.expo.dev/workflow/customizing/ "Add custom native code to an Expo project"
