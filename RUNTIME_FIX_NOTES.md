# Runtime алдааны засвар

## Олдсон шалтгаан

`teleproto` нь `cifrante`-ээр дамжуулан криптографийн санамсаргүй байт шаарддаг. Өмнөх хувилбарт `react-native-get-random-values` ашигласан бөгөөд энэ сан Android-ийн хуучин синхрон `NativeModules.RNGetRandomValues.getRandomBase64()` bridge-ийг дууддаг. Төслийн Android тохиргоонд React Native New Architecture асаалттай тул энэ хуучин синхрон bridge-д найдах нь runtime-д `undefined is not a function` үүсгэх эрсдэлтэй. Үүнээс болж Telegram нэвтрэх/пост унших шатанд зогсож, Bot API таталтын хүсэлтэд хүрэхгүй байв.

## Хийсэн өөрчлөлт

- `react-native-get-random-values`-ийг dependency-гээс авсан.
- Expo SDK 57-д тохирсон `expo-crypto` нэмсэн.
- `index.js` эхлэх үед `expo-crypto.getRandomBytes()`-оор WebCrypto-ийн `crypto.getRandomValues()`-ийг нөхөж, 65,536 байтаас их хүсэлтийг Expo Crypto-ийн 1,024 байтын хязгаарыг баримтлан жижиг хэсгүүдээр авдаг болгосон.
- `Object.hasOwn()` байхгүй Hermes runtime-д зориулсан fallback-ийг хэвээр үлдээж, app-ийн бусад кодоос өмнө ажиллуулдаг болгосон.
- APK-ийн native library-г жижиглэсэн, ARM64 build тохиргоог хэвээр хадгалсан.

## Шалгалт

- `pnpm check`: амжилттай.
- `pnpm test`: 31 test амжилттай, 1 test skip.
- Expo Crypto Android эх кодод `getRandomValues` нь Expo Modules `Function`-оор зарлагдаж, Java `SecureRandom` ашигладгийг шалгасан.
- Энэ sandbox-д физик Android төхөөрөмж холбож бодит Telegram video download хийх боломжгүй байсан; тиймээс төхөөрөмж дээрх end-to-end download тест амжилттай гэж энд батлаагүй.

## APK дахин build хийх

Android Studio эсвэл Android SDK/JDK 21 орчинд project root-оос:

```bash
cd android
./gradlew assembleRelease
```

APK гарах зам: `android/app/build/outputs/apk/release/app-release.apk`.
