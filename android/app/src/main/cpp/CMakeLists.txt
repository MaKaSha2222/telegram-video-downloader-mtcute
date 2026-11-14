cmake_minimum_required(VERSION 3.22.1)
project(localbotapi LANGUAGES CXX)

set(TELEGRAM_BOT_API_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../vendor/telegram-bot-api")

if(NOT EXISTS "${TELEGRAM_BOT_API_ROOT}/CMakeLists.txt")
  message(FATAL_ERROR "Pinned telegram-bot-api source is missing from vendor/")
endif()

set(TD_ENABLE_JNI OFF CACHE BOOL "" FORCE)
set(TD_ENABLE_LTO OFF CACHE BOOL "" FORCE)
if(ANDROID)
  find_package(ZLIB REQUIRED)
  if(NOT DEFINED ENV{OPENSSL_ROOT_DIR} OR "$ENV{OPENSSL_ROOT_DIR}" STREQUAL "")
    message(FATAL_ERROR "OPENSSL_ROOT_DIR must point to the Android OpenSSL installation")
  endif()
  set(OPENSSL_ROOT_DIR "$ENV{OPENSSL_ROOT_DIR}")
  set(OPENSSL_INCLUDE_DIR "${OPENSSL_ROOT_DIR}/include")
  set(OPENSSL_CRYPTO_LIBRARY "${OPENSSL_ROOT_DIR}/lib/libcrypto.a")
  if(NOT EXISTS "${OPENSSL_INCLUDE_DIR}/openssl/crypto.h" OR NOT EXISTS "${OPENSSL_CRYPTO_LIBRARY}")
    message(FATAL_ERROR "Android OpenSSL headers or libcrypto.a are missing under ${OPENSSL_ROOT_DIR}")
  endif()
  set(OPENSSL_LIBRARIES "${OPENSSL_CRYPTO_LIBRARY}")
  set(OPENSSL_FOUND TRUE)
endif()
add_subdirectory("${TELEGRAM_BOT_API_ROOT}/td" "${CMAKE_CURRENT_BINARY_DIR}/td")
if(TARGET tdjson)
  set_target_properties(tdjson PROPERTIES EXCLUDE_FROM_ALL TRUE)
endif()

set(TELEGRAM_BOT_API_SOURCES
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/Client.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/ClientManager.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/HttpConnection.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/HttpStatConnection.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/Query.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/Stats.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/Watchdog.cpp"
  "${TELEGRAM_BOT_API_ROOT}/telegram-bot-api/WebhookActor.cpp"
)

add_library(localbotapi SHARED
  local_bot_api_jni.cpp
  local_bot_api_engine.cpp
  local_bot_api_progress.cpp
  ${TELEGRAM_BOT_API_SOURCES}
)

target_include_directories(localbotapi PRIVATE
  "${CMAKE_CURRENT_LIST_DIR}"
  "${TELEGRAM_BOT_API_ROOT}"
  "${TELEGRAM_BOT_API_ROOT}/td"
  "${TELEGRAM_BOT_API_ROOT}/td/tdactor"
  "${TELEGRAM_BOT_API_ROOT}/td/tddb"
  "${TELEGRAM_BOT_API_ROOT}/td/tdnet"
  "${TELEGRAM_BOT_API_ROOT}/td/tdutils"
  "${TELEGRAM_BOT_API_ROOT}/td/tdtelegram"
  "${TELEGRAM_BOT_API_ROOT}/td/td/generate/auto"
)

if(NOT ANDROID)
  file(GLOB JAVA_HOME_CANDIDATES "/usr/lib/jvm/*")
  find_path(JNI_INCLUDE_DIR jni.h
    HINTS "$ENV{JAVA_HOME}" ${JAVA_HOME_CANDIDATES}
    PATH_SUFFIXES include)
  find_path(JNI_MD_INCLUDE_DIR jni_md.h
    HINTS "$ENV{JAVA_HOME}" ${JAVA_HOME_CANDIDATES}
    PATH_SUFFIXES include/linux)
  if(NOT JNI_INCLUDE_DIR OR NOT JNI_MD_INCLUDE_DIR)
    message(FATAL_ERROR "JDK JNI headers were not found; set JAVA_HOME to a JDK installation")
  endif()
  target_include_directories(localbotapi PRIVATE ${JNI_INCLUDE_DIR} ${JNI_MD_INCLUDE_DIR})
endif()

target_compile_features(localbotapi PRIVATE cxx_std_17)
target_compile_options(localbotapi PRIVATE -fexceptions -fno-rtti -fPIC -ffunction-sections -fdata-sections -Wall -Wextra -Werror=return-type)
target_link_libraries(localbotapi PRIVATE memprof tdactor tdcore tddb tdnet tdutils)
if(ANDROID)
  target_link_options(localbotapi PRIVATE -Wl,--gc-sections)
  if(CMAKE_STRIP)
    add_custom_command(TARGET localbotapi POST_BUILD
      COMMAND "${CMAKE_STRIP}" --strip-unneeded "$<TARGET_FILE:localbotapi>"
      COMMENT "Strip debug symbols from the Android Bot API native library")
  endif()
  target_link_libraries(localbotapi PRIVATE log android)
endif()
