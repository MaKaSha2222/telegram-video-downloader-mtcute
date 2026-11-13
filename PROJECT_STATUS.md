# Telegram Video Downloader — ပရောဂျက်အခြေအနေ

ဒီ ZIP ထဲမှာ Android app ရဲ့ လက်ရှိ source project ကို ထည့်ထားပါတယ်။ ဒါက APK မဟုတ်သေးပါ။

## လက်ရှိပါဝင်နေသောအပိုင်းများ

- Telegram link ထည့်ရန် ပင်မမျက်နှာပြင်နှင့် API ID၊ API Hash၊ Bot Token ကို Android SecureStore ထဲသိမ်းရန် Settings မျက်နှာပြင်။
- Public Telegram link ခွဲထုတ်ခြင်း၊ Bot API `file_id` ဖွဲ့စည်းခြင်းနှင့် app အတွင်း MTProto client အတွက် TypeScript code။
- Link parser နှင့် `file_id` အတွက် unit test ၆ ခု။
- Android target ABI ကို `arm64-v8a` တစ်ခုတည်းထားသော Expo configuration။
- Android foreground-service permissions (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) ကို configuration ထဲ ထည့်ထားပြီး native service မရှိသေးသည့်အတွက် အလုပ်လုပ်ပြီးပြီဟု မယူဆရသေးပါ။
- Official `tdlib/telegram-bot-api` source ကို commit `7232881b39dfcd048a0d569f1983c5184249ee05` တွင် pin လုပ်ထားပြီး upstream TDLib submodule commit `c8828d50f5eac2fc7089ce1089d7ce9095c61b5e` ကို စစ်ဆေးထားပါတယ်။ Native integration အတွက် လမ်းညွှန်ကို `native-implementation.md` တွင် ရေးထားပါတယ်။

## အရေးကြီးသော ကန့်သတ်ချက်

Kotlin/C++ native module source ကို ထည့်သွင်းပြီး upstream Bot API scheduler ကို in-process lifecycle၊ loopback binding၊ foreground service နှင့် JNI/Expo module boundary သို့ ချိတ်ဆက်ထားပါတယ်။ Host-side AArch64 CMake build သည် `100% Built target localbotapi` ဖြင့် အောင်မြင်ပြီး TDLib/Bot API shared library link ပြီးပါပြီ။ ဒါပေမဲ့ Android `arm64-v8a` NDK build နဲ့ device test မပြီးသေးတဲ့အတွက် ဒီအဆင့်ရှိ project ကို install လုပ်ပြီး အမှန်တကယ် video download လုပ်၍ရပြီဟု မဆိုနိုင်သေးပါ။

ဒီ project ကို မြင်ကွင်းနှင့် source logic စစ်ဆေးရန်အတွက်သာ ပေးထားပါတယ်။ အလုပ်လုပ်ပြီးသား downloader သို့မဟုတ် အဆင်သင့် APK အဖြစ် မယူဆပါနှင့်။

## စစ်ဆေးခဲ့သော command များ

```bash
pnpm install
pnpm exec tsc --noEmit
pnpm exec vitest run tests/telegram-link.test.ts tests/telegram-file-id.test.ts
pnpm exec expo export --platform android --no-bytecode --output-dir /tmp/tgvidapp-app-export
```

Таталтын бодит ажиллагаа болон Android native build хараахан шалгагдаагүй байна.
