#pragma once
#include <cstdint>
namespace SheShield {
uint8_t readPreference(const char *key, uint8_t fallback);
bool writePreference(const char *key, uint8_t value);
} // namespace SheShield
