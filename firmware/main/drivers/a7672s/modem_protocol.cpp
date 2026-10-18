#include "modem_protocol.h"
#include <algorithm>
#include <cerrno>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

namespace SheShield::A76xx {
static std::string trim(std::string s) {
    auto first = s.find_first_not_of(" \r\n\t");
    return first == std::string::npos ? ""
                                      : s.substr(first, s.find_last_not_of(" \r\n\t") - first + 1);
}
bool validPhone(const std::string &phone) {
    size_t start = !phone.empty() && phone[0] == '+' ? 1 : 0;
    size_t digits = phone.size() - start;
    if (digits < 3 || digits > 20)
        return false;
    for (size_t i = start; i < phone.size(); ++i)
        if (phone[i] < '0' || phone[i] > '9')
            return false;
    return true;
}
static bool utf8Bmp(const std::string &s, std::vector<uint16_t> &out) {
    for (size_t i = 0; i < s.size();) {
        unsigned char c = s[i++];
        uint32_t cp;
        unsigned extra;
        if (c < 0x80) {
            cp = c;
            extra = 0;
        } else if (c >= 0xc2 && c <= 0xdf) {
            cp = c & 31;
            extra = 1;
        } else if (c >= 0xe0 && c <= 0xef) {
            cp = c & 15;
            extra = 2;
        } else
            return false; // SMS UCS-2 supports BMP, no silent emoji corruption.
        if (i + extra > s.size())
            return false;
        for (unsigned k = 0; k < extra; ++k) {
            unsigned char b = s[i++];
            if ((b & 0xc0) != 0x80)
                return false;
            cp = (cp << 6) | (b & 63);
        }
        if ((extra == 1 && cp < 0x80) || (extra == 2 && cp < 0x800) ||
            (cp >= 0xd800 && cp <= 0xdfff) || cp == 0 || cp == 0x1a)
            return false;
        out.push_back(static_cast<uint16_t>(cp));
    }
    return !out.empty();
}
static void hexByte(std::string &s, unsigned b) {
    const char *h = "0123456789ABCDEF";
    s += h[(b >> 4) & 15];
    s += h[b & 15];
}
bool encodeSms(const std::string &phone, const std::string &text, uint8_t reference,
               std::vector<SmsPart> &out) {
    out.clear();
    std::vector<uint16_t> chars;
    if (!validPhone(phone) || !utf8Bmp(text, chars) || chars.size() > 6 * 67)
        return false;
    bool multipart = chars.size() > 70;
    size_t capacity = multipart ? 67 : 70;
    unsigned total = (chars.size() + capacity - 1) / capacity;
    std::string digits = phone[0] == '+' ? phone.substr(1) : phone;
    for (unsigned part = 0; part < total; ++part) {
        SmsPart p;
        p.hex = "00"; // Use SIM-provisioned SMSC. Do not overwrite carrier settings.
        hexByte(p.hex, multipart ? 0x41 : 0x01);
        hexByte(p.hex, 0); // SMS-SUBMIT, no validity period
        hexByte(p.hex, digits.size());
        hexByte(p.hex, phone[0] == '+' ? 0x91 : 0x81);
        for (size_t i = 0; i < digits.size(); i += 2)
            hexByte(p.hex,
                    ((i + 1 < digits.size() ? digits[i + 1] - '0' : 15) << 4) | (digits[i] - '0'));
        hexByte(p.hex, 0);
        hexByte(p.hex, 8); // PID=0; UCS-2 encoding
        size_t first = part * capacity, count = std::min(capacity, chars.size() - first);
        hexByte(p.hex, count * 2 + (multipart ? 6 : 0));
        if (multipart) {
            for (unsigned b : {5u, 0u, 3u, unsigned(reference), total, part + 1})
                hexByte(p.hex, b);
        }
        for (size_t i = first; i < first + count; ++i) {
            hexByte(p.hex, chars[i] >> 8);
            hexByte(p.hex, chars[i]);
        }
        p.tpduBytes = p.hex.size() / 2 - 1;
        out.push_back(p);
    }
    return true;
}
static bool number(const std::string &s, double &value) {
    if (s.empty())
        return false;
    char *end = nullptr;
    value = std::strtod(s.c_str(), &end);
    return end && *end == '\0' && std::isfinite(value);
}
bool parseGpsInfo(const std::string &line, Fix &fix) {
    fix = {};
    const std::string prefix = "+CGPSINFO:";
    if (line.compare(0, prefix.size(), prefix))
        return false;
    std::vector<std::string> fields;
    auto rest = trim(line.substr(prefix.size()));
    size_t pos = 0;
    do {
        auto comma = rest.find(',', pos);
        fields.push_back(trim(rest.substr(pos, comma == std::string::npos ? comma : comma - pos)));
        if (comma == std::string::npos)
            break;
        pos = comma + 1;
    } while (true);
    if (fields.size() < 6 || (fields[1] != "N" && fields[1] != "S") ||
        (fields[3] != "E" && fields[3] != "W"))
        return false;
    double lat, lon;
    if (!number(fields[0], lat) || !number(fields[2], lon) || lat < 0 || lon < 0)
        return false;
    double latDeg = std::floor(lat / 100), lonDeg = std::floor(lon / 100),
           latMin = lat - latDeg * 100, lonMin = lon - lonDeg * 100;
    if (latMin >= 60 || lonMin >= 60 || latDeg > 90 || lonDeg > 180 ||
        (latDeg == 90 && latMin != 0) || (lonDeg == 180 && lonMin != 0))
        return false;
    if (fields[4].size() != 6 || fields[5].size() < 6)
        return false;
    for (char c : fields[4])
        if (c < '0' || c > '9')
            return false;
    double utc;
    if (!number(fields[5], utc) || utc < 0 || utc >= 240000 || int(utc / 10000) > 23 ||
        int(utc / 100) % 100 > 59 || std::fmod(utc, 100) >= 60)
        return false;
    unsigned day = std::atoi(fields[4].substr(0, 2).c_str()),
             month = std::atoi(fields[4].substr(2, 2).c_str());
    if (day < 1 || day > 31 || month < 1 || month > 12)
        return false;
    fix.valid = true;
    fix.latitude = (latDeg + latMin / 60) * (fields[1] == "S" ? -1 : 1);
    fix.longitude = (lonDeg + lonMin / 60) * (fields[3] == "W" ? -1 : 1);
    fix.utc = fields[4] + " " + fields[5] + " UTC";
    return true;
}
static bool twoIntegers(const std::string &line, long &first, long &second) {
    auto colon = line.find(':');
    if (colon == std::string::npos)
        return false;
    const char *cursor = line.c_str() + colon + 1;
    char *end = nullptr;
    errno = 0;
    first = std::strtol(cursor, &end, 10);
    if (errno || end == cursor)
        return false;
    while (*end == ' ' || *end == '\t')
        ++end;
    if (*end++ != ',')
        return false;
    cursor = end;
    errno = 0;
    second = std::strtol(cursor, &end, 10);
    if (errno || end == cursor)
        return false;
    while (*end == ' ' || *end == '\t')
        ++end;
    return !*end || *end == ',';
}
int parseRegistration(const std::string &s) {
    if (s.rfind("+CEREG:", 0) && s.rfind("+CREG:", 0))
        return -1;
    long mode, state;
    return twoIntegers(s, mode, state) && mode >= 0 && mode <= 5 && state >= 0 && state <= 10
               ? static_cast<int>(state)
               : -1;
}
int parseCsq(const std::string &s) {
    if (s.rfind("+CSQ:", 0))
        return 99;
    long csq, ber;
    return twoIntegers(s, csq, ber) && csq >= 0 && csq <= 31 && ber >= 0 && ber <= 99
               ? static_cast<int>(csq)
               : 99;
}
Response Protocol::receive(uint32_t timeout, const char *prefix, bool prompt) {
    Response out;
    std::string line;
    bool data = prefix == nullptr, overflow = false;
    int64_t deadline = io.nowMs() + timeout;
    while (io.nowMs() < deadline) {
        int ch = io.readByte(std::min<int64_t>(50, deadline - io.nowMs()));
        if (ch < 0)
            continue;
        if (prompt && ch == '>' && trim(line).empty()) {
            out.reply = Reply::Prompt;
            return out;
        }
        if (ch == '\r')
            continue;
        if (ch != '\n') {
            if (line.size() < 511)
                line += char(ch);
            else
                overflow = true;
            continue;
        }
        auto text = trim(line);
        line.clear();
        if (overflow) {
            desynchronized = true;
            return out;
        }
        if (text.empty())
            continue;
        if (text == "ERROR" || text.rfind("+CME ERROR:", 0) == 0 ||
            text.rfind("+CMS ERROR:", 0) == 0 || text == "NO CARRIER" || text == "BUSY" ||
            text == "NO ANSWER") {
            out.reply = Reply::Error;
            return out;
        }
        if (text == "OK") {
            if (data) {
                out.reply = Reply::Ok;
                return out;
            }
            continue;
        }
        if (prefix && text.rfind(prefix, 0) == 0)
            data = true;
        if (out.lines.size() < 24)
            out.lines.push_back(text); // Bounds unsolicited chatter/NMEA.
    }
    desynchronized = true;
    return out;
}
Response Protocol::command(const std::string &cmd, uint32_t timeout, const char *prefix,
                           bool prompt) {
    if (cmd.find_first_of("\r\n\x1a") != std::string::npos)
        return {Reply::Error, {}};
    auto bytes = cmd + "\r";
    if (!io.write(bytes.data(), bytes.size())) {
        desynchronized = true;
        return {};
    }
    return receive(timeout, prefix, prompt);
}
bool Protocol::synchronize() {
    // Escape a possibly abandoned SMS prompt, drain a bounded interval, then require
    // a CSQ response as a barrier so a late OK cannot masquerade as a new AT reply.
    const char escape = 27;
    io.write(&escape, 1);
    int64_t end = io.nowMs() + 1000;
    while (io.nowMs() < end)
        io.readByte(50);
    auto r = command("AT+CSQ", 2000, "+CSQ:");
    desynchronized = r.reply != Reply::Ok;
    return !desynchronized;
}
bool Protocol::initialize() {
    if (!synchronize())
        return false;
    auto identity = command("AT+CGMM", 3000);
    bool model = false;
    for (auto &line : identity.lines)
        if (line.find("A7672") != std::string::npos)
            model = true;
    if (identity.reply != Reply::Ok || !model)
        return false; // Do not run on a wrong/legacy modem.
    for (const char *cmd : {"ATE0", "AT+IFC=0,0", "AT+CMEE=2", "AT+CFUN=1", "AT+CMGF=0"})
        if (command(cmd, 5000).reply != Reply::Ok)
            return false;
    // GNSS support depends on the actual module SKU/firmware. Failure remains visible as no fix.
    command("AT+CGNSSTST=0", 3000); // Stop a pre-existing NMEA stream on the command UART.
    if (desynchronized)
        return false;
    command("AT+CGNSSPWR=1", 5000);
    return !desynchronized;
}
int Protocol::registration() {
    for (const char *cmd : {"AT+CEREG?", "AT+CREG?"}) {
        auto r = command(cmd, 3000, cmd[4] == 'E' ? "+CEREG:" : "+CREG:");
        for (auto &line : r.lines) {
            int state = parseRegistration(line);
            if (r.reply == Reply::Ok && state >= 0) {
                if (state == 1 || state == 5)
                    return state;
                if (cmd[4] != 'E')
                    return state;
            }
        }
        if (needsSync())
            return -1;
    }
    return -1;
}
int Protocol::signal() {
    auto r = command("AT+CSQ", 2000, "+CSQ:");
    for (auto &line : r.lines)
        if (r.reply == Reply::Ok && line.rfind("+CSQ:", 0) == 0)
            return parseCsq(line);
    return 99;
}
bool Protocol::location(Fix &fix) {
    fix = {};
    auto r = command("AT+CGPSINFO", 3000, "+CGPSINFO:");
    for (auto &line : r.lines)
        if (r.reply == Reply::Ok && parseGpsInfo(line, fix))
            return true;
    return false;
}
SmsOutcome Protocol::sendSms(const std::string &phone, const std::string &text, uint8_t reference) {
    SmsOutcome outcome;
    std::vector<SmsPart> parts;
    if (!encodeSms(phone, text, reference, parts))
        return outcome;
    outcome.total = parts.size();
    if (command("AT+CMGF=0").reply != Reply::Ok)
        return outcome;
    for (auto &part : parts) {
        auto r = command("AT+CMGS=" + std::to_string(part.tpduBytes), 5000, nullptr, true);
        if (r.reply != Reply::Prompt) {
            outcome.state = outcome.accepted ? SendState::Partial : SendState::Rejected;
            return outcome;
        }
        auto payload = part.hex + char(26);
        if (!io.write(payload.data(), payload.size())) {
            desynchronized = true;
            outcome.state = outcome.accepted ? SendState::Partial : SendState::Unknown;
            return outcome;
        }
        r = receive(60000, "+CMGS:", false);
        if (r.reply != Reply::Ok) {
            outcome.state = outcome.accepted ? SendState::Partial : SendState::Unknown;
            return outcome;
        }
        ++outcome.accepted;
    }
    outcome.state = SendState::Accepted;
    return outcome;
}
Reply Protocol::dial(const std::string &phone) {
    if (!validPhone(phone))
        return Reply::Error;
    return command("ATD" + phone + ";", 10000).reply;
}
bool Protocol::hangup() { return command("ATH", 5000).reply == Reply::Ok; }
} // namespace SheShield::A76xx
