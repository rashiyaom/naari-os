#pragma once
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <string>
#include <vector>
namespace SheShield {
struct StoredMemo {
    std::string id, sha256;
    size_t bytes = 0;
    int64_t timestampMs = 0;
    bool synced = false;
};
class MemoStore {
  public:
    static MemoStore &instance();
    bool init();
    bool canCapture() const;
    bool save(const int16_t *pcm, size_t samples); // Copies immediately, commits on storage worker.
    bool busy() const { return saving; }
    std::string error() const;
    std::vector<StoredMemo> catalog() const;
    // 490 bytes fits the negotiated 512-byte BLE MTU with the 10-byte memo
    // header and ATT overhead, cutting transfer round-trips by more than half.
    std::vector<uint8_t> chunk(const std::string &id, size_t offset, size_t maxPayload = 490) const;
    bool readPcm(const std::string &id, int16_t *destination, size_t capacitySamples,
                 size_t &samples) const;
    bool remove(const std::string &id); // Explicit user deletion of the watch copy only.
    bool acknowledge(const std::string &id, const std::string &hash);

  private:
    MemoStore() = default;
    void commit(void *data, size_t bytes);
    mutable SemaphoreHandle_t mutex = nullptr;
    std::atomic<bool> ready{false}, saving{false};
    std::vector<StoredMemo> memos;
    std::string lastError;
};
} // namespace SheShield
