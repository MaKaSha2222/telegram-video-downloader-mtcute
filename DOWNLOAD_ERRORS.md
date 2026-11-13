# ဗီဒီယိုဒေါင်းလုဒ် အမှားများ

## ဖြစ်နိုင်တဲ့ အမှားအမျိုးအစားများ

ဒေါင်းလုဒ်လုပ်ငန်းစဉ်မှာ အဆင့်အမျိုးမျိုးရှိတာကြောင့် အမှားအရင်းအမြစ်လည်း တစ်မျိုးတည်း မဟုတ်ပါ။ Link ပုံစံမမှန်ခြင်း၊ post မရှိတော့ခြင်း၊ public post ဖြစ်ပေမယ့် bot က မဖတ်နိုင်ခြင်း၊ post ထဲမှာ video မပါခြင်း၊ Telegram API credentials မှားခြင်း၊ Telethon rate limit (`FloodWaitError`), file reference သက်တမ်းကုန်ခြင်း၊ Bot API က `file_id` ကိုပယ်ချခြင်း၊ local server မစတင်နိုင်ခြင်း၊ loopback ချိတ်ဆက်မှု timeout/refused ဖြစ်ခြင်း၊ notification ခွင့်ပြုချက်မရှိခြင်းနဲ့ storage ပြည့်ခြင်းတို့ ဖြစ်နိုင်ပါတယ်။

Telegram Bot API ရဲ့ မအောင်မြင်တဲ့ response မှာ `ok: false`, ဖတ်လို့ရတဲ့ `description`, `error_code` နဲ့ တစ်ခါတလေ `parameters` ပါနိုင်ပါတယ်။ Telegram က error code တွေ ပြောင်းနိုင်တယ်လို့ သတ်မှတ်ထားပါတယ်။ Telethon ကလည်း သိထားတဲ့ RPC error စာရင်းဟာ အပြည့်အစုံအာမခံထားတာ မဟုတ်ဘဲ best-effort ဖြစ်တယ်လို့ ရှင်းပြထားပါတယ်။ ဒါကြောင့် app က ဖြစ်နိုင်တဲ့ error စကားလုံးတွေကို ကြိုတင်စာရင်းကန့်သတ်မထားဘဲ အချိန်တကယ်ပြန်လာတဲ့ type၊ code နဲ့ message ကို ပြပါတယ်။ [1] [2]

Telegram ရဲ့ အများသုံး hosted `getFile` စာမျက်နှာမှာ 20 MB download limit ကို ဖော်ပြထားပါတယ်။ Local Bot API Server အတွက်တော့ Telegram က download size limit မရှိနိုင်ကြောင်း ဖော်ပြထားပါတယ်။ ဒီ app က in-app Local Bot API Server ကို သုံးပေမယ့် ဖုန်း storage မလုံလောက်တာ၊ network/server မတုံ့ပြန်တာတွေကြောင့် download မအောင်မြင်နိုင်ပါသေးတယ်။ [3] [4]

## Error ပြတဲ့အခါ ပါဝင်မယ့်အချက်များ

Error panel မှာ အမှားအမျိုးအစား (type)၊ ရရှိနိုင်ရင် RPC/HTTP/Bot API code၊ ပြည့်စုံတဲ့ message နဲ့ အကြောင်းရင်းအဆင့်ဆင့်ကို ပြပါတယ်။ `FloodWaitError` လို error တွေမှာ စောင့်ရမယ့် စက္ကန့်အရေအတွက်လို အသေးစိတ် attribute တွေကိုပါ ဖော်ပြပါတယ်။ Bot API error ဖြစ်ရင် HTTP status, `parameters` နဲ့ server ကပြန်ပေးတဲ့ response body အပြည့်အစုံကို ထည့်ပြပါတယ်။ Java network layer က connection အမှားတွေကို `IOException` အမျိုးအစားတွေနဲ့ ပြန်ပေးနိုင်ပြီး connect/read timeout သီးခြားသတ်မှတ်နိုင်တာကြောင့် app က timeout တန်ဖိုးတွေနဲ့ exception type ကိုပါ ပြပါတယ်။ လက်ရှိ `getFile` connection timeout က 15 စက္ကန့်၊ read timeout က 60 စက္ကန့်ဖြစ်ပါတယ်။ [5]

အောက်ပါအမှားအုပ်စုတွေကို ဖော်ထုတ်ပြထားပါတယ်။

- **Link/Post:** link မမှန်ခြင်း၊ public post မဟုတ်ခြင်း၊ post မရှိတော့ခြင်း၊ channel ကို bot မဖတ်နိုင်ခြင်း၊ video မပါခြင်း။
- **Telethon/Telegram RPC:** တကယ့် exception class နဲ့ message၊ ရရှိနိုင်ရင် RPC code၊ wait seconds သို့မဟုတ် DC ပြောင်းရွှေ့ရန်လိုအပ်မှုလို attribute များ။ မသိသေးတဲ့ RPC error ဖြစ်လည်း class/message ကို ပြပါတယ်။
- **Bot API Server:** native server startup error၊ `getFile` HTTP status၊ Bot API `error_code`, `description`, `parameters`, JSON မမှန်ခြင်း၊ `file_path` မရခြင်း၊ server file ပျောက်ခြင်း။
- **Network/Android:** timeout, connection refused, notification permission, foreground service မစတင်နိုင်ခြင်းနဲ့ network policy အမှားများ။
- **Storage/Copy:** directory မဖန်တီးနိုင်ခြင်း၊ file ဖတ်/ရေးမရခြင်း၊ disk ပြည့်ခြင်း၊ file မပြည့်မီကူးယူမိခြင်း။

Bot API နဲ့ Telethon အမှားစာရင်းတွေ ပြောင်းလဲနိုင်တဲ့အတွက် UI က ကြိုတင်သတ်မှတ်ထားတဲ့ ဘာသာပြန်စာတစ်ခုကို ခန့်မှန်းမပြပါ။ Server က အချိန်တကယ်ပြန်ပေးတဲ့ message ကိုပြပါတယ်။ Saved Bot Token နဲ့ API Hash ကို error text ထဲက ဖျောက်ထားပါတယ်။

## ရင်းမြစ်များ

[1]: https://core.telegram.org/bots/api "Telegram Bot API request and response format"
[2]: https://docs.telethon.dev/en/stable/concepts/errors.html "Telethon RPC error handling"
[3]: https://core.telegram.org/bots/api/methods/getfile "Telegram Bot API getFile method"
[4]: https://core.telegram.org/bots/api#using-a-local-bot-api-server "Using a Local Bot API Server"
[5]: https://developer.android.com/reference/java/net/HttpURLConnection "Android HttpURLConnection reference"
