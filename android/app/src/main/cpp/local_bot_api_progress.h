#pragma once

#include <cstdint>
#include <string>

namespace local_bot_api {

struct DownloadProgress {
  std::int64_t downloaded_size = 0;
  std::int64_t size = 0;
  std::int64_t expected_size = 0;
  bool is_downloading_active = false;
  bool is_downloading_completed = false;
};

void begin_download_progress(const std::string &bot_file_id, std::int32_t td_file_id,
                             const DownloadProgress &progress);
void update_download_progress(std::int32_t td_file_id, const DownloadProgress &progress);
bool get_download_progress(const std::string &bot_file_id, DownloadProgress *progress);
void clear_download_progress(const std::string &bot_file_id);

}  // namespace local_bot_api
