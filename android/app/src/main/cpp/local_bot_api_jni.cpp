#include "local_bot_api_engine.h"
#include "local_bot_api_progress.h"

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

extern "C" JNIEXPORT jstring JNICALL
Java_expo_modules_localbotapi_LocalBotApiModule_nativeGetDownloadProgress(JNIEnv *env, jclass, jstring bot_file_id) {
  local_bot_api::DownloadProgress progress;
  if (!local_bot_api::get_download_progress(to_string(env, bot_file_id), &progress)) {
    return env->NewStringUTF("");
  }
  const auto json = std::string("{\"downloaded_size\":") + std::to_string(progress.downloaded_size) +
                    ",\"size\":" + std::to_string(progress.size) +
                    ",\"expected_size\":" + std::to_string(progress.expected_size) +
                    ",\"is_downloading_active\":" + (progress.is_downloading_active ? "true" : "false") +
                    ",\"is_downloading_completed\":" +
                    (progress.is_downloading_completed ? "true" : "false") + "}";
  return env->NewStringUTF(json.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_expo_modules_localbotapi_LocalBotApiModule_nativeClearDownloadProgress(JNIEnv *env, jclass, jstring bot_file_id) {
  local_bot_api::clear_download_progress(to_string(env, bot_file_id));
}
