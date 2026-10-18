#!/usr/bin/env python3
"""Exercise the real persistent PCM reader with files, host locks and SHA-256.
Requires clang++ and Homebrew OpenSSL 3 on this macOS development environment.
"""
import hashlib
import struct
import subprocess
import tempfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
source = (root / 'main/storage/memo_store.cpp').read_text()
reader = source[source.index('bool MemoStore::readPcm('):source.index('bool MemoStore::acknowledge(')]
validators = source[source.index('bool validId('):source.index('std::string hashFile(')]
fixture = r'''
#include <algorithm>
#include <atomic>
#include <cassert>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cerrno>
#include <unistd.h>
#include <sys/stat.h>
#define ESP_LOGI(...) ((void)0)
#include <mutex>
#include <string>
#include <vector>
#include <openssl/sha.h>
#include "storage/memo_store.h"
int mbedtls_sha256(const unsigned char *p, size_t n, unsigned char *out, int) {
    return SHA256(p, n, out) ? 0 : -1;
}
namespace SheShield {
const char *base;
struct Guard {
    SemaphoreHandle_t m;
    Guard(SemaphoreHandle_t m) : m(m) { if(m) m->lock(); }
    ~Guard() { if(m) m->unlock(); }
};
'''
fixture += validators
fixture += r'''
MemoStore &MemoStore::instance() { static MemoStore store; return store; }
bool MemoStore::init() {
    static std::mutex guard;
    mutex = &guard;
    StoredMemo memo;
    memo.id = std::string(32, 'a');
    memo.bytes = 8;
    char hash[65];
    auto file = fopen(path(memo.id, ".info").c_str(), "rb");
    assert(file && fscanf(file, "%64s", hash) == 1); fclose(file);
    memo.sha256 = hash; memos.push_back(memo);
    ready = true;
    return true;
}
std::vector<StoredMemo> MemoStore::catalog() const { return memos; }
'''
fixture += reader + '\n}\n'
fixture += r'''
int main(int argc, char **argv) {
    using namespace SheShield;
    assert(argc == 2); base = argv[1];
    auto &store = MemoStore::instance(); store.init();
    int16_t pcm[4] = {}; size_t samples = 999;
    auto id = std::string(32, 'a');
    assert(store.readPcm(id, pcm, 4, samples) && samples == 4);
    assert(pcm[0] == -32768 && pcm[1] == -100 && pcm[2] == 0 && pcm[3] == 32767);
    assert(!store.readPcm(id, pcm, 3, samples) && samples == 0);
    assert(!store.readPcm("../../etc/passwd", pcm, 4, samples));
    assert(!store.readPcm(std::string(32, 'b'), pcm, 4, samples));
    assert(!store.readPcm(id, nullptr, 4, samples));
    auto file = fopen(path(id, ".pcm").c_str(), "ab"); assert(file);
    fputc(0, file); fclose(file);
    assert(!store.readPcm(id, pcm, 4, samples)); // Extra data, not silently truncated.
    file = fopen(path(id, ".pcm").c_str(), "wb"); assert(file);
    fwrite("abcdefgh", 1, 8, file); fclose(file);
    assert(!store.readPcm(id, pcm, 4, samples)); // Same length, wrong hash.
    file = fopen(path(id, ".pcm").c_str(), "wb"); assert(file);
    fwrite("abc", 1, 3, file); fclose(file);
    assert(!store.readPcm(id, pcm, 4, samples)); // Truncated PCM.
    assert(!store.remove("../../etc/passwd"));
    assert(!store.remove(std::string(32, 'b')));
    // A failed unlink must preserve the catalog and must not remove metadata.
    assert(unlink(path(id, ".pcm").c_str()) == 0);
    assert(mkdir(path(id, ".pcm").c_str(), 0700) == 0);
    assert(!store.remove(id) && store.catalog().size() == 1);
    assert(access(path(id, ".info").c_str(), F_OK) == 0);
    assert(rmdir(path(id, ".pcm").c_str()) == 0);
    file = fopen(path(id, ".pcm").c_str(), "wb"); assert(file);
    fwrite(pcm, 1, 8, file); fclose(file);
    file = fopen(path(id, ".ack").c_str(), "wb"); assert(file); fclose(file);
    // A separate phone copy is outside the watch recording's file set.
    file = fopen(path(id, ".phone.wav").c_str(), "wb"); assert(file);
    fwrite("keep", 1, 4, file); fclose(file);
    assert(store.remove(id) && store.catalog().empty());
    assert(access(path(id, ".pcm").c_str(), F_OK) != 0);
    assert(access(path(id, ".info").c_str(), F_OK) != 0);
    assert(access(path(id, ".ack").c_str(), F_OK) != 0);
    assert(access(path(id, ".phone.wav").c_str(), F_OK) == 0);
    assert(!store.readPcm(id, pcm, 4, samples));
    std::puts("Stored memo reader/delete: files, bounds, checksum, cancellation, unlink failure, sidecars and phone copy: PASS");
    std::puts("Stored memo reader: existing PCM, capacity, IDs, truncation, overflow, SHA-256 mismatch: PASS");
}
'''
with tempfile.TemporaryDirectory(prefix='naari-memo-reader-') as temp:
    temp = Path(temp)
    (temp / 'freertos').mkdir()
    (temp / 'freertos/FreeRTOS.h').write_text('#pragma once\n')
    (temp / 'freertos/semphr.h').write_text('#pragma once\n#include <mutex>\nusing SemaphoreHandle_t=std::mutex*;\n')
    pcm = struct.pack('<hhhh', -32768, -100, 0, 32767)
    (temp / ('a' * 32 + '.pcm')).write_bytes(pcm)
    (temp / ('a' * 32 + '.info')).write_text(hashlib.sha256(pcm).hexdigest())
    (temp / 'test.cpp').write_text(fixture)
    openssl = Path('/opt/homebrew/opt/openssl@3')
    subprocess.run(['clang++', '-std=c++17', '-g', '-fsanitize=address,undefined',
                    '-I' + str(temp), '-I' + str(root / 'main'), '-I' + str(openssl / 'include'),
                    str(temp / 'test.cpp'), '-L' + str(openssl / 'lib'), '-lcrypto',
                    '-o', str(temp / 'test')], check=True)
    subprocess.run([str(temp / 'test'), str(temp)], check=True)
