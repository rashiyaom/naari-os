#pragma once
// Hardware-independent A76XX protocol; production and transcript tests share this code.
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace SheShield::A76xx {
class Io {
  public:
    virtual ~Io() = default;
    virtual bool write(const char *data, size_t size) = 0;
    virtual int readByte(uint32_t timeoutMs) = 0;
    virtual int64_t nowMs() const = 0;
};
enum class Reply { Ok, Error, Timeout, Prompt };
struct Response {
    Reply reply = Reply::Timeout;
    std::vector<std::string> lines;
};
struct Fix {
    bool valid = false;
    double latitude = 0, longitude = 0;
    std::string utc;
};
struct SmsPart {
    std::string hex;
    size_t tpduBytes = 0;
};
enum class SendState { Accepted, Rejected, Unknown, Partial };
struct SmsOutcome {
    SendState state = SendState::Rejected;
    unsigned accepted = 0, total = 0;
};
bool validPhone(const std::string &phone);
bool encodeSms(const std::string &phone, const std::string &utf8, uint8_t reference,
               std::vector<SmsPart> &parts);
bool parseGpsInfo(const std::string &line, Fix &fix);
int parseRegistration(const std::string &line);
int parseCsq(const std::string &line);
class Protocol {
  public:
    explicit Protocol(Io &io) : io(io) {}
    Response command(const std::string &command, uint32_t timeoutMs = 3000,
                     const char *requiredPrefix = nullptr, bool prompt = false);
    bool synchronize();
    bool initialize();
    int registration();
    int signal();
    bool location(Fix &fix);
    SmsOutcome sendSms(const std::string &phone, const std::string &text, uint8_t reference);
    Reply dial(const std::string &phone);
    bool hangup();
    bool needsSync() const { return desynchronized; }

  private:
    Response receive(uint32_t timeoutMs, const char *requiredPrefix, bool prompt);
    Io &io;
    bool desynchronized = false;
};
} // namespace SheShield::A76xx
