#include "local_bot_api_engine.h"

#include "telegram-bot-api/ClientManager.h"
#include "telegram-bot-api/ClientParameters.h"
#include "telegram-bot-api/HttpConnection.h"
#include "telegram-bot-api/HttpServer.h"
#include "td/actor/ConcurrentScheduler.h"
#include "td/actor/actor.h"
#include "td/net/GetHostByNameActor.h"
#include "td/net/HttpInboundConnection.h"
#include "td/telegram/ClientActor.h"
#include "td/utils/crypto.h"
#include "td/utils/logging.h"
#include "td/utils/port/thread.h"
#include "td/utils/Time.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <memory>
#include <exception>
#include <thread>

namespace local_bot_api {

namespace {

class EngineState final {
 public:
  std::mutex mutex;
  std::condition_variable ready;
  std::thread thread;
  std::atomic_bool stop_requested{false};
  bool running = false;
  int port = 0;
  std::string startup_error;
};

EngineState &state() {
  static EngineState value;
  return value;
}

void set_startup_error(const std::string &message) {
  auto &engine = state();
  std::lock_guard<std::mutex> lock(engine.mutex);
  engine.startup_error = message;
  engine.running = false;
  engine.ready.notify_all();
}

void run_server(std::int32_t api_id, std::string api_hash, std::string working_directory,
                std::string files_directory, int port) {
  auto &engine = state();
#if TD_HAVE_OPENSSL
  td::init_openssl_threads();
#endif

  auto shared_data = std::make_shared<telegram_bot_api::SharedData>();
  auto parameters = std::make_shared<telegram_bot_api::ClientParameters>();
  parameters->version_ = "10.4-android";
  parameters->shared_data_ = shared_data;
  parameters->start_time_ = td::Time::now();
  parameters->api_id_ = api_id;
  parameters->api_hash_ = std::move(api_hash);
  parameters->local_mode_ = true;
  parameters->working_directory_ = std::move(working_directory);
  parameters->files_directory_ = std::move(files_directory);
  parameters->net_query_stats_ = td::create_net_query_stats();

  td::ConcurrentScheduler scheduler(telegram_bot_api::SharedData::get_thread_count() - 1);
  auto get_host_by_name_options = td::GetHostByNameActor::Options();
  get_host_by_name_options.scheduler_id = telegram_bot_api::SharedData::get_dns_resolver_scheduler_id();
  parameters->get_host_by_name_actor_id_ =
      scheduler.create_actor_unsafe<td::GetHostByNameActor>(0, "GetHostByName", std::move(get_host_by_name_options))
          .release();

  auto client_manager = scheduler
                            .create_actor_unsafe<telegram_bot_api::ClientManager>(
                                telegram_bot_api::SharedData::get_client_scheduler_id(), "ClientManager", parameters,
                                telegram_bot_api::ClientManager::TokenRange{0, 1})
                            .release();

  scheduler
      .create_actor_unsafe<telegram_bot_api::HttpServer>(
          telegram_bot_api::SharedData::get_client_scheduler_id(), "HttpServer", td::string("127.0.0.1"), port,
          [client_manager, shared_data] {
            return td::ActorOwn<td::HttpInboundConnection::Callback>(
                td::create_actor<telegram_bot_api::HttpConnection>("HttpConnection", client_manager, shared_data));
          })
      .release();

  scheduler.start();
  {
    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.running = true;
    engine.port = port;
    engine.startup_error.clear();
    engine.ready.notify_all();
  }

  bool close_requested = false;
  bool can_quit = false;
  while (true) {
    scheduler.run_main(0.25);
    if (engine.stop_requested.load() && !close_requested) {
      close_requested = true;
      auto guard = scheduler.get_main_guard();
      send_closure(client_manager, &telegram_bot_api::ClientManager::close,
                   td::PromiseCreator::lambda([&can_quit](td::Unit) { can_quit = true; }));
    }
    if (can_quit) break;
  }

  scheduler.finish();
  {
    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.running = false;
    engine.port = 0;
    engine.ready.notify_all();
  }
}

}  // namespace

Engine &Engine::instance() {
  static Engine engine;
  return engine;
}

Status Engine::status() const {
  auto &engine = state();
  std::lock_guard<std::mutex> lock(engine.mutex);
  return Status{engine.running, engine.port};
}

bool Engine::start(std::int64_t api_id, const std::string &api_hash, const std::string &working_directory,
                   const std::string &files_directory, int port, std::string *error) {
  auto &engine = state();
  std::unique_lock<std::mutex> lock(engine.mutex);
  if (engine.thread.joinable()) {
    if (engine.running) return true;
    lock.unlock();
    engine.thread.join();
    lock.lock();
  }
  engine.stop_requested.store(false);
  engine.startup_error.clear();
  engine.thread = std::thread([api_id, api_hash, working_directory, files_directory, port] {
    try {
      run_server(static_cast<std::int32_t>(api_id), api_hash, working_directory, files_directory, port);
    } catch (const std::exception &error) {
      set_startup_error(error.what());
    } catch (...) {
      set_startup_error("Unknown Bot API native engine error");
    }
  });
  engine.ready.wait_for(lock, std::chrono::seconds(10), [&engine] { return engine.running || !engine.startup_error.empty(); });
  if (!engine.running) {
    if (error != nullptr) *error = engine.startup_error.empty() ? "Bot API engine startup timed out" : engine.startup_error;
    lock.unlock();
    if (engine.thread.joinable()) engine.thread.join();
    return false;
  }
  return true;
}

void Engine::stop() {
  auto &engine = state();
  {
    std::lock_guard<std::mutex> lock(engine.mutex);
    engine.stop_requested.store(true);
  }
  if (engine.thread.joinable()) engine.thread.join();
}

}  // namespace local_bot_api
