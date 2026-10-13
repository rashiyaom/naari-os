#!/usr/bin/env python3
"""Test real phone request/ACK code with host locks, NVS, clock and radio stubs.
Transport callbacks are tested by ESP-IDF compilation and device boot; this
suite checks parsing, queue state, result correlation, expiry and persistence.
"""
from pathlib import Path
import os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
IDF=Path(os.environ.get('IDF_PATH',Path.home()/'esp/esp-idf'))
src=(ROOT/'main/network/phone_bridge.cpp').read_text()
state=src[src.index('static SemaphoreHandle_t mutex;'):src.index('// BLE UUID byte order')]
helpers=src[src.index('static const char *stringValue'):src.index('static int access')]
methods=src[src.index('void PhoneBridge::openPairing()'):src.rindex('} // namespace SheShield')]
fixture=r'''
#include "network/phone_bridge.h"
#include <cJSON.h>
#include <mutex>
#include <map>
#include <cstdio>
#include <cstring>
#include <cmath>
#include <cassert>
#include <algorithm>
using SemaphoreHandle_t=std::mutex*;
constexpr int portMAX_DELAY=0, BLE_HS_CONN_HANDLE_NONE=65535, BLE_ERR_REM_USER_CONN_TERM=19, NVS_READWRITE=1;
void xSemaphoreTake(SemaphoreHandle_t p,int){p->lock();}
void xSemaphoreGive(SemaphoreHandle_t p){p->unlock();}
int64_t clockUs=2000000;
int64_t esp_timer_get_time(){return clockUs;}
uint32_t esp_random(){return 12345;}
using nvs_handle_t=int;
std::map<std::string,std::string> stored;
bool failNvs=false;
int nvs_open(const char*,int,nvs_handle_t* h){*h=1;return failNvs?1:0;}
int nvs_set_str(nvs_handle_t,const char* k,const char* v){stored[k]=v;return 0;}
int nvs_commit(nvs_handle_t){return 0;}
void nvs_close(nvs_handle_t){}
void ble_gap_terminate(int,int){}
void ble_store_clear(){}
void* ble_hs_mbuf_from_flat(const void*,size_t){return nullptr;}
void ble_gatts_notify_custom(int,int,void*){}
namespace SheShield {
'''
fixture+=state+helpers+'''
PhoneBridge& PhoneBridge::instance(){static PhoneBridge p;return p;}
esp_err_t PhoneBridge::init(){mutex=new std::mutex;sequence=100;return ESP_OK;}
'''+methods+'}\n'
tests=r'''
using namespace SheShield;
bool ack(uint32_t id,const char* status,const char* extra=""){
    return PhoneBridge::instance().acceptReply(("{\"v\":1,\"id\":"+std::to_string(id)+",\"status\":\""+status+"\""+extra+"}").c_str());
}
int main(){
    auto& p=PhoneBridge::instance();p.init();
    assert(!p.request("sms","1234567","Hi"));
    assert(!p.acceptReply("not json"));assert(!p.acceptReply("{\"op\":\"hello\",\"v\":2}"));
    link.connected=true;
    assert(p.acceptReply("{\"v\":1,\"op\":\"hello\",\"sms\":true,\"calls\":true,\"location\":true}"));
    assert(p.status().ready&&p.status().smsAllowed);
    assert(!p.request("sms","bad number","Hi"));assert(!p.request("unknown","1234567","Hi"));
    assert(!p.request("sms","1234567",std::string(257,'x').c_str()));
    auto sms=p.request("sms","1234567","Quoted \"text\" and newline\n");assert(sms);
    auto json=cJSON_Parse(p.pendingJson().c_str());assert(json);
    assert(std::string(cJSON_GetObjectItem(json,"message")->valuestring)=="Quoted \"text\" and newline\n");cJSON_Delete(json);
    assert(!ack(sms+1,"sent"));assert(!ack(sms,"calling"));assert(!ack(sms,"made_up"));
    assert(p.result(sms).state==PhoneRequestState::Queued);
    assert(ack(sms,"received"));assert(count==1);assert(p.result(sms).state==PhoneRequestState::Received);
    assert(ack(sms,"sent"));assert(count==0);assert(ack(sms,"sent")); // idempotent write retry
    assert(ack(sms,"delivered"));assert(ack(sms,"delivered"));assert(!ack(sms,"received"));
    auto loc=p.request("location");assert(loc);assert(!ack(loc,"sent"));assert(!ack(loc,"located"));
    assert(!ack(loc,"located",",\"lat\":200,\"lon\":20"));assert(count==1);
    assert(ack(loc,"located",",\"lat\":28.5,\"lon\":77.1"));assert(p.status().locationValid);
    assert(p.result(loc).state==PhoneRequestState::Located);assert(count==0);
    auto call=p.request("call","1234567");assert(call);assert(!ack(call,"sent"));assert(ack(call,"needs_phone"));
    assert(p.result(call).state==PhoneRequestState::NeedsPhone);
    auto frac=p.request("sms","1234567","Hi");assert(frac);
    assert(!p.acceptReply(("{\"v\":1,\"id\":"+std::to_string(frac)+".5,\"status\":\"sent\"}").c_str()));assert(ack(frac,"failed"));
    for(int i=0;i<16;++i)assert(p.request("sms","1234567","Queue"));assert(!p.request("sms","1234567","Full"));
    while(count)assert(ack(pending[head].id,"failed"));
    auto timeout=p.request("call","1234567");clockUs+=31000000;p.tick();assert(p.result(timeout).state==PhoneRequestState::Failed);
    clockUs+=15000000;p.tick();assert(!p.status().ready);assert(!p.request("location"));
    clockUs+=75000000;assert(!p.status().locationValid);
    assert(p.acceptReply("{\"v\":1,\"op\":\"profile\",\"name0\":\"Mom\",\"phone0\":\"1234567\",\"name1\":\"Sister\",\"phone1\":\"9876543\"}"));
    assert(p.guardianName(0)=="Mom"&&p.guardianPhone(1)=="9876543"&&stored["phone0"]=="1234567");
    assert(!p.acceptReply("{\"v\":1,\"op\":\"profile\",\"phone0\":\"BAD\"}"));assert(p.guardianPhone(0)=="1234567");
    failNvs=true;assert(!p.acceptReply("{\"v\":1,\"op\":\"profile\",\"phone0\":\"1112223\"}"));assert(p.guardianPhone(0)=="1234567");
    p.openPairing();assert(p.isPairing());assert(p.passkey()>=100000&&p.passkey()<=999999);clockUs+=61000000;assert(!p.isPairing());
    delete mutex;
    std::puts("Phone protocol: bounds, JSON escaping, ACK correlation/type/idempotency, location validation, queue saturation, expiry, profile errors, pairing window: PASS");
}
'''
with tempfile.TemporaryDirectory(prefix='naari-protocol-') as tmp:
    tmp=Path(tmp);(tmp/'esp_err.h').write_text('#pragma once\nusing esp_err_t=int;\n#define ESP_OK 0\n')
    (tmp/'test.cpp').write_text(fixture+tests)
    cjson=IDF/'components/json/cJSON'
    common=['-Wno-deprecated-declarations','-g','-O1','-fsanitize=address,undefined','-I'+str(tmp),'-I'+str(ROOT/'main'),'-I'+str(cjson)]
    subprocess.run(['clang',*common,'-c',str(cjson/'cJSON.c'),'-o',str(tmp/'json.o')],check=True)
    subprocess.run(['clang++','-std=c++17',*common,str(tmp/'test.cpp'),str(tmp/'json.o'),'-o',str(tmp/'test')],check=True)
    subprocess.run([str(tmp/'test')],check=True)
