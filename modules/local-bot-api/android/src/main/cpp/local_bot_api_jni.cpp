#include "local_bot_api_engine.h"

#include <jni.h>

#include <string>

namespace {

std::string to_string(JNIEnv *env, jstring value) {
  if (value == nullptr) return {};
  const char *chars = env->GetStringUTFChars(value, nullptr);
  std::string result = chars == nullptr ? std::string() : std::string(chars);
  if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
  return result;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_expo_modules_localbotapi_LocalBotApiModule_nativeStart(
    JNIEnv *env, jclass, jlong api_id, jstring api_hash, jstring working_directory, jstring files_directory,
    jint port) {
  std::string error;
  const bool started = local_bot_api::Engine::instance().start(
      static_cast<std::int64_t>(api_id), to_string(env, api_hash), to_string(env, working_directory),
      to_string(env, files_directory), static_cast<int>(port), &error);
  return started ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_expo_modules_localbotapi_LocalBotApiModule_nativeStop(JNIEnv *, jclass) {
  local_bot_api::Engine::instance().stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_expo_modules_localbotapi_LocalBotApiModule_nativeIsRunning(JNIEnv *, jclass) {
  return local_bot_api::Engine::instance().status().running ? JNI_TRUE : JNI_FALSE;
}
