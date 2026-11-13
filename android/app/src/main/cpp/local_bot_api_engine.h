#pragma once

#include <cstdint>
#include <mutex>
#include <string>

namespace local_bot_api {

struct Status {
  bool running = false;
  int port = 0;
};

class Engine final {
 public:
  static Engine &instance();

  Status status() const;
  bool start(std::int64_t api_id, const std::string &api_hash, const std::string &working_directory,
             const std::string &files_directory, int port, std::string *error);
  void stop();

 private:
  Engine() = default;
  mutable std::mutex mutex_;
  bool running_ = false;
  int port_ = 0;
};

}  // namespace local_bot_api
