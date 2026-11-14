#include "local_bot_api_progress.h"

#include <mutex>
#include <algorithm>
#include <unordered_map>
#include <unordered_set>

namespace local_bot_api {
namespace {
std::mutex progress_mutex;
std::unordered_map<std::string, DownloadProgress> progress_by_bot_file_id;
std::unordered_map<std::int32_t, std::unordered_set<std::string>> bot_file_ids_by_td_file_id;
}  // namespace

void begin_download_progress(const std::string &bot_file_id, std::int32_t td_file_id,
                             const DownloadProgress &progress) {
  std::lock_guard<std::mutex> lock(progress_mutex);
  bot_file_ids_by_td_file_id[td_file_id].insert(bot_file_id);
  auto normalized = progress;
  normalized.size = std::max(normalized.size, normalized.expected_size);
  progress_by_bot_file_id[bot_file_id] = normalized;
}

void update_download_progress(std::int32_t td_file_id, const DownloadProgress &progress) {
  std::lock_guard<std::mutex> lock(progress_mutex);
  auto ids = bot_file_ids_by_td_file_id.find(td_file_id);
  if (ids == bot_file_ids_by_td_file_id.end()) return;
  auto normalized = progress;
  normalized.size = std::max(normalized.size, normalized.expected_size);
  for (const auto &bot_file_id : ids->second) {
    auto current = progress_by_bot_file_id.find(bot_file_id);
    if (current != progress_by_bot_file_id.end()) current->second = normalized;
  }
}

bool get_download_progress(const std::string &bot_file_id, DownloadProgress *progress) {
  if (progress == nullptr) return false;
  std::lock_guard<std::mutex> lock(progress_mutex);
  auto current = progress_by_bot_file_id.find(bot_file_id);
  if (current == progress_by_bot_file_id.end()) return false;
  *progress = current->second;
  return true;
}

void clear_download_progress(const std::string &bot_file_id) {
  std::lock_guard<std::mutex> lock(progress_mutex);
  auto current = progress_by_bot_file_id.find(bot_file_id);
  if (current == progress_by_bot_file_id.end()) return;
  for (auto it = bot_file_ids_by_td_file_id.begin(); it != bot_file_ids_by_td_file_id.end();) {
    it->second.erase(bot_file_id);
    if (it->second.empty()) it = bot_file_ids_by_td_file_id.erase(it);
    else ++it;
  }
  progress_by_bot_file_id.erase(current);
}

}  // namespace local_bot_api
