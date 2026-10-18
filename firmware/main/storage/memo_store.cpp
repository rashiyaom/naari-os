#include "memo_store.h"
#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <ctime>
#include <dirent.h>
#include <esp_heap_caps.h>
#include <esp_log.h>
#include <esp_partition.h>
#include <esp_random.h>
#include <esp_vfs_fat.h>
#include <freertos/task.h>
#include <mbedtls/sha256.h>
#include <sys/stat.h>
#include <unistd.h>
namespace SheShield {
namespace {
const char *base = "/memos";
struct Guard {
    SemaphoreHandle_t m;
    Guard(SemaphoreHandle_t m) : m(m) {
        if (m)
            xSemaphoreTake(m, portMAX_DELAY);
    }
    ~Guard() {
        if (m)
            xSemaphoreGive(m);
    }
};
bool validId(const std::string &id) {
    return id.size() == 32 && std::all_of(id.begin(), id.end(), [](char c) {
               return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
           });
}
std::string path(const std::string &id, const char *ext) {
    return std::string(base) + "/" + id + ext;
}
std::string hashFile(const std::string &file) {
    FILE *f = fopen(file.c_str(), "rb");
    if (!f)
        return "";
    mbedtls_sha256_context ctx;
    mbedtls_sha256_init(&ctx);
    mbedtls_sha256_starts(&ctx, 0);
    uint8_t buffer[1024], digest[32];
    size_t n;
    while ((n = fread(buffer, 1, sizeof(buffer), f)))
        mbedtls_sha256_update(&ctx, buffer, n);
    bool ok = !ferror(f);
    fclose(f);
    mbedtls_sha256_finish(&ctx, digest);
    mbedtls_sha256_free(&ctx);
    if (!ok)
        return "";
    char hex[65];
    for (unsigned i = 0; i < 32; ++i)
        snprintf(hex + i * 2, 3, "%02x", digest[i]);
    return hex;
}
bool durableClose(FILE *f) {
    bool ok = fflush(f) == 0;
    if (ok)
        ok = fsync(fileno(f)) == 0;
    return fclose(f) == 0 && ok;
}
} // namespace
MemoStore &MemoStore::instance() {
    static MemoStore s;
    return s;
}
bool MemoStore::init() {
    mutex = xSemaphoreCreateMutex();
    if (!mutex)
        return false;
    auto p = esp_partition_find_first(ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_DATA_FAT,
                                      "storage");
    if (!p) {
        lastError = "Memo partition unavailable";
        return false;
    }
    // Only an entirely erased partition may be formatted automatically. Never erase a damaged
    // existing volume.
    bool blank = true;
    uint8_t bytes[1024];
    for (size_t offset = 0; offset < p->size && blank; offset += sizeof(bytes)) {
        if (esp_partition_read(p, offset, bytes, sizeof(bytes)) != ESP_OK) {
            blank = false;
            break;
        }
        for (auto b : bytes)
            if (b != 255) {
                blank = false;
                break;
            }
    }
    esp_vfs_fat_mount_config_t cfg = {};
    cfg.max_files = 5;
    cfg.allocation_unit_size = 4096;
    cfg.format_if_mount_failed = blank;
    wl_handle_t handle;
    if (esp_vfs_fat_spiflash_mount_rw_wl(base, "storage", &cfg, &handle) != ESP_OK) {
        // Preserve a nonblank/unrecognized flash volume. Use a dedicated folder on an
        // already mounted SD card, without formatting or altering existing evidence files.
        struct stat card;
        base = "/sdcard/naari-memos";
        if (stat("/sdcard", &card) || !S_ISDIR(card.st_mode) ||
            (mkdir(base, 0700) && (stat(base, &card) || !S_ISDIR(card.st_mode)))) {
            lastError = "Memo storage unavailable; insert SD card";
            return false;
        }
    }
    ESP_LOGI("Memo_Store", "Recording storage: %s", base);
    DIR *dir = opendir(base);
    if (!dir) {
        lastError = "Memo catalog unavailable";
        return false;
    }
    while (auto entry = readdir(dir)) {
        std::string name = entry->d_name;
        if (name.size() == 36 && name.substr(32) == ".tmp" && validId(name.substr(0, 32))) {
            unlink(path(name.substr(0, 32), ".tmp").c_str());
            continue;
        }
        if (name.size() != 36 || name.substr(32) != ".pcm")
            continue;
        StoredMemo memo;
        memo.id = name.substr(0, 32);
        if (!validId(memo.id))
            continue;
        struct stat st;
        if (stat(path(memo.id, ".pcm").c_str(), &st) || st.st_size <= 0 || st.st_size > 320000 ||
            st.st_size % 2)
            continue;
        memo.bytes = st.st_size;
        memo.sha256 = hashFile(path(memo.id, ".pcm"));
        if (memo.sha256.empty())
            continue;
        FILE *info = fopen(path(memo.id, ".info").c_str(), "rb");
        if (info) {
            long long ms = 0;
            if (fscanf(info, "%lld", &ms) == 1)
                memo.timestampMs = ms;
            fclose(info);
        }
        memo.synced = access(path(memo.id, ".ack").c_str(), F_OK) == 0;
        memos.push_back(memo);
    }
    closedir(dir);
    std::sort(memos.begin(), memos.end(),
              [](const StoredMemo &a, const StoredMemo &b) { return a.id < b.id; });
    ready = true;
    return true;
}
bool MemoStore::canCapture() const {
    if (!ready || saving)
        return false;
    Guard lock(mutex);
    if (!ready || saving)
        return false;
    return memos.size() < 6 ||
           std::any_of(memos.begin(), memos.end(), [](const StoredMemo &m) { return m.synced; });
}
std::string MemoStore::error() const {
    Guard lock(mutex);
    return lastError;
}
std::vector<StoredMemo> MemoStore::catalog() const {
    Guard lock(mutex);
    return memos;
}
bool MemoStore::save(const int16_t *pcm, size_t samples) {
    if (!pcm || !samples || samples > 160000 || !canCapture())
        return false;
    size_t bytes = samples * 2;
    void *copy = heap_caps_malloc(bytes, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT);
    if (!copy) {
        Guard lock(mutex);
        lastError = "Memo save memory unavailable";
        return false;
    }
    memcpy(copy, pcm, bytes);
    saving = true;
    struct Work {
        void *data;
        size_t bytes;
    };
    auto work = new Work{copy, bytes};
    if (xTaskCreate(
            [](void *arg) {
                auto work = static_cast<Work *>(arg);
                MemoStore::instance().commit(work->data, work->bytes);
                free(work->data);
                delete work;
                vTaskDelete(nullptr);
            },
            "memo_save", 6144, work, 2, nullptr) != pdPASS) {
        free(copy);
        delete work;
        saving = false;
        Guard lock(mutex);
        lastError = "Memo save worker unavailable";
        return false;
    }
    return true;
}
void MemoStore::commit(void *data, size_t bytes) {
    Guard lock(mutex);
    if (memos.size() >= 6) {
        auto old =
            std::find_if(memos.begin(), memos.end(), [](const StoredMemo &m) { return m.synced; });
        if (old == memos.end()) {
            lastError = "Storage full; sync recordings first";
            saving = false;
            return;
        }
        for (auto ext : {".pcm", ".info", ".ack"})
            unlink(path(old->id, ext).c_str());
        memos.erase(old);
    }
    StoredMemo memo;
    char id[33];
    snprintf(id, sizeof(id), "%08lx%08lx%08lx%08lx", (unsigned long)esp_random(),
             (unsigned long)esp_random(), (unsigned long)esp_random(), (unsigned long)esp_random());
    memo.id = id;
    memo.bytes = bytes;
    memo.timestampMs = time(nullptr) > 1600000000 ? int64_t(time(nullptr)) * 1000 : 0;
    auto temp = path(memo.id, ".tmp");
    FILE *f = fopen(temp.c_str(), "wb");
    bool ok = false;
    if (f) {
        bool written = fwrite(data, 1, bytes, f) == bytes;
        ok = durableClose(f) && written;
    }
    if (ok)
        ok = rename(temp.c_str(), path(memo.id, ".pcm").c_str()) == 0;
    if (ok) {
        memo.sha256 = hashFile(path(memo.id, ".pcm"));
        ok = !memo.sha256.empty();
    }
    if (ok) {
        FILE *info = fopen(path(memo.id, ".info").c_str(), "wb");
        if (info) {
            fprintf(info, "%lld", (long long)memo.timestampMs);
            durableClose(info);
        }
        memos.push_back(memo);
        lastError.clear();
    } else {
        unlink(temp.c_str());
        lastError = "Memo save failed; keep watch powered";
    }
    saving = false;
}
std::vector<uint8_t> MemoStore::chunk(const std::string &id, size_t offset,
                                      size_t maxPayload) const {
    Guard lock(mutex);
    auto m =
        std::find_if(memos.begin(), memos.end(), [&](const StoredMemo &m) { return m.id == id; });
    if (m == memos.end() || offset >= m->bytes)
        return {};
    size_t n = std::min({maxPayload, size_t(230), m->bytes - offset});
    std::vector<uint8_t> packet(10 + n);
    uint32_t seq = offset;
    for (unsigned i = 0; i < 4; ++i) {
        packet[i] = (seq >> (8 * i)) & 255;
        packet[4 + i] = (offset >> (8 * i)) & 255;
    }
    packet[8] = n & 255;
    packet[9] = n >> 8;
    FILE *f = fopen(path(id, ".pcm").c_str(), "rb");
    if (!f)
        return {};
    bool ok = fseek(f, offset, SEEK_SET) == 0 && fread(packet.data() + 10, 1, n, f) == n;
    fclose(f);
    return ok ? packet : std::vector<uint8_t>{};
}
bool MemoStore::readPcm(const std::string &id, int16_t *destination, size_t capacitySamples,
                        size_t &samples) const {
    samples = 0;
    if (!destination || !validId(id))
        return false;
    Guard lock(mutex);
    auto memo =
        std::find_if(memos.begin(), memos.end(), [&](const StoredMemo &m) { return m.id == id; });
    if (memo == memos.end() || !memo->bytes || memo->bytes % 2 || memo->bytes / 2 > capacitySamples)
        return false;
    FILE *file = fopen(path(id, ".pcm").c_str(), "rb");
    if (!file)
        return false;
    bool ok = fread(destination, 1, memo->bytes, file) == memo->bytes && fgetc(file) == EOF &&
              !ferror(file);
    fclose(file);
    if (!ok)
        return false;
    uint8_t digest[32];
    if (mbedtls_sha256(reinterpret_cast<const unsigned char *>(destination), memo->bytes, digest,
                       0))
        return false;
    char hex[65];
    for (unsigned i = 0; i < 32; ++i)
        snprintf(hex + i * 2, 3, "%02x", digest[i]);
    if (memo->sha256 != hex)
        return false;
    samples = memo->bytes / 2;
    return true;
}
bool MemoStore::remove(const std::string &id) {
    if (!ready || saving || !validId(id))
        return false;
    Guard lock(mutex);
    if (saving)
        return false;
    auto memo =
        std::find_if(memos.begin(), memos.end(), [&](const StoredMemo &m) { return m.id == id; });
    if (memo == memos.end())
        return false;
    // Remove the PCM first. On failure, preserve the catalog entry for retry.
    // A reboot cannot rediscover a deleted PCM even if sidecar cleanup is interrupted.
    if (unlink(path(id, ".pcm").c_str()) != 0 && errno != ENOENT)
        return false;
    memos.erase(memo);
    for (const char *extension : {".info", ".ack"})
        unlink(path(id, extension).c_str());
    ESP_LOGI("Memo_Store", "Watch recording deleted; %u remaining", unsigned(memos.size()));
    return true;
}
bool MemoStore::acknowledge(const std::string &id, const std::string &hash) {
    Guard lock(mutex);
    for (auto &m : memos)
        if (m.id == id && m.sha256 == hash) {
            if (m.synced)
                return true;
            FILE *f = fopen(path(id, ".ack").c_str(), "wb");
            if (!f || !durableClose(f))
                return false;
            m.synced = true;
            return true;
        }
    return false;
}
} // namespace SheShield
