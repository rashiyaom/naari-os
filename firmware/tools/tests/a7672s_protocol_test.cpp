#include "drivers/a7672s/modem_protocol.h"
#include <cassert>
#include <cmath>
#include <deque>
#include <iostream>
using namespace SheShield::A76xx;
struct Step {
    std::string write, response;
};
class Fake : public Io {
  public:
    std::deque<Step> steps;
    std::string rx;
    size_t pos = 0;
    int64_t clock = 100;
    bool write(const char *bytes, size_t size) override {
        assert(!steps.empty());
        auto step = steps.front();
        steps.pop_front();
        if (std::string(bytes, size) != step.write) {
            std::cerr << "Unexpected command in transcript\n";
            assert(false);
        }
        rx = step.response;
        pos = 0;
        return true;
    }
    int readByte(uint32_t timeout) override {
        if (pos < rx.size()) {
            ++clock;
            return static_cast<unsigned char>(rx[pos++]);
        }
        clock += timeout;
        return -1;
    }
    int64_t nowMs() const override { return clock; }
};
int main() {
    assert(validPhone("+919876543210"));
    assert(validPhone("112"));
    for (auto bad : {"", "+", "12", "+123;ATH", "123\rAT", "++123", "abc"})
        assert(!validPhone(bad));
    assert(parseRegistration("+CEREG: 0,1") == 1);
    assert(parseRegistration("+CEREG: 2,5,\"1234\",\"12345678\",7") == 5);
    assert(parseRegistration("+CREG: 0,2") == 2);
    assert(parseRegistration("+CEREG: 0,1bad") == -1);
    assert(parseRegistration("+CEREG: 1") == -1);
    assert(parseRegistration("+CEREG: 0,9999999999999999999999999") == -1);
    assert(parseCsq("+CSQ: 31,99") == 31);
    assert(parseCsq("+CSQ: 9999999999999999999999999,0") == 99);
    assert(parseCsq("+CSQ: 99,99") == 99);
    assert(parseCsq("+CSQ: -1,0") == 99);
    Fix fix;
    assert(parseGpsInfo("+CGPSINFO:3113.343286,N,12121.234064,E,250311,072809.33,44.1,0.0,0", fix));
    assert(std::abs(fix.latitude - 31.2223881) < 0.000001);
    assert(std::abs(fix.longitude - 121.3539010667) < 0.000001);
    assert(parseGpsInfo("+CGPSINFO:0030.000,S,00030.000,W,171026,120000.0", fix));
    assert(fix.latitude == -0.5 && fix.longitude == -0.5);
    for (auto bad :
         {"+CGPSINFO:,,,,,,,,", "+CGPSINFO:9160,N,12121,E,171026,120000",
          "+CGPSINFO:3159,N,18100,E,171026,120000", "+CGPSINFO:nan,N,12121,E,171026,120000",
          "+CGPSINFO:3113,Q,12121,E,171026,120000", "+CGPSINFO:3113,N,12121,E,171026,256000",
          "+CGPSINFO:3113,N,12121,E,171026,1e2000",
          "+CGPSINFO:3113,N,12121,E,171026,100000000000000000000000000"}) {
        assert(!parseGpsInfo(bad, fix));
        assert(!fix.valid);
    }
    std::vector<SmsPart> parts;
    assert(encodeSms("+12345", "Hi", 7, parts));
    assert(parts.size() == 1);
    assert(parts[0].hex == "00010005912143F500080400480069");
    assert(parts[0].tpduBytes == 14);
    assert(encodeSms("+12345", std::string(71, 'A'), 7, parts));
    assert(parts.size() == 2);
    assert(parts[0].hex.find("050003070201") != std::string::npos);
    assert(parts[1].hex.find("050003070202") != std::string::npos);
    assert(encodeSms("+12345", u8"नारी", 7, parts));
    assert(!encodeSms("+12345", u8"😀", 7, parts));
    assert(parts.empty());
    assert(!encodeSms("+12345", std::string(403, 'A'), 7, parts));
    assert(!encodeSms("+12345", std::string("\xC0\x80", 2), 7, parts));
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CSQ\r", "\r\n+CREG: 1\r\nOK\r\n+CSQ: 18,99\r\nOK\r\n"}};
        assert(p.signal() == 18);
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CEREG?\r", "+CEREG: 0,5\r\nOK\r\n"}};
        assert(p.registration() == 5);
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CEREG?\r", "+CEREG: 0,2\r\nOK\r\n"},
                    {"AT+CREG?\r", "+CREG: 0,1\r\nOK\r\n"}};
        assert(p.registration() == 1);
    }
    assert(encodeSms("+12345", "Hi", 7, parts));
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CMGF=0\r", "OK\r\n"},
                    {"AT+CMGS=14\r", "\r\n> "},
                    {parts[0].hex + char(26), "+CMGS: 42\r\nOK\r\n"}};
        auto r = p.sendSms("+12345", "Hi", 7);
        assert(r.state == SendState::Accepted && r.accepted == 1 && io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CMGF=0\r", "OK\r\n"}, {"AT+CMGS=14\r", "+CMS ERROR: 302\r\n"}};
        auto r = p.sendSms("+12345", "Hi", 7);
        assert(r.state == SendState::Rejected && r.accepted == 0 && io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {
            {"AT+CMGF=0\r", "OK\r\n"}, {"AT+CMGS=14\r", "> "}, {parts[0].hex + char(26), ""}};
        auto r = p.sendSms("+12345", "Hi", 7);
        assert(r.state == SendState::Unknown && r.accepted == 0 && p.needsSync() &&
               io.steps.empty());
    }
    assert(encodeSms("+12345", std::string(71, 'A'), 7, parts));
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CMGF=0\r", "OK\r\n"},
                    {"AT+CMGS=" + std::to_string(parts[0].tpduBytes) + "\r", "> "},
                    {parts[0].hex + char(26), "+CMGS: 1\r\nOK\r\n"},
                    {"AT+CMGS=" + std::to_string(parts[1].tpduBytes) + "\r", "> "},
                    {parts[1].hex + char(26), ""}};
        auto r = p.sendSms("+12345", std::string(71, 'A'), 7);
        assert(r.state == SendState::Partial && r.accepted == 1 && r.total == 2 &&
               io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"ATD+12345;\r", "OK\r\n"}, {"ATH\r", "OK\r\n"}};
        assert(p.dial("+12345") == Reply::Ok);
        assert(p.hangup());
        assert(p.dial("+12345;ATH") == Reply::Error && io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"ATD+12345;\r", "BUSY\r\n"}};
        assert(p.dial("+12345") == Reply::Error);
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{std::string(1, char(27)), "late OK\r\n"},
                    {"AT+CSQ\r", "OK\r\n+CSQ: 18,99\r\nOK\r\n"},
                    {"AT+CGMM\r", "SIMCOM_A7672S\r\nOK\r\n"},
                    {"ATE0\r", "OK\r\n"},
                    {"AT+IFC=0,0\r", "OK\r\n"},
                    {"AT+CMEE=2\r", "OK\r\n"},
                    {"AT+CFUN=1\r", "OK\r\n"},
                    {"AT+CMGF=0\r", "OK\r\n"},
                    {"AT+CGNSSTST=0\r", "OK\r\n"},
                    {"AT+CGNSSPWR=1\r", "OK\r\n"}};
        assert(p.initialize() && !p.needsSync() && io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{std::string(1, char(27)), ""},
                    {"AT+CSQ\r", "+CSQ: 18,99\r\nOK\r\n"},
                    {"AT+CGMM\r", "SIM800L\r\nOK\r\n"}};
        assert(!p.initialize() && io.steps.empty());
    }
    {
        Fake io;
        Protocol p(io);
        io.steps = {{"AT+CSQ\r", std::string(600, 'X') + "\r\nOK\r\n"}};
        assert(p.command("AT+CSQ").reply == Reply::Timeout && p.needsSync());
    }
    std::cout << "A7672S transcript tests: identity, UART framing, registration, GNSS, "
                 "PDU/multipart/Unicode, SMS ambiguity/no retries, dialing/hangup: PASS\n";
}
