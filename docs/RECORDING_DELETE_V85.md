# Watch v8.5: delete saved recordings

Open **Voice Memos → select a recording → Delete recording**. The confirmation screen offers **Delete from watch** and **Keep recording**. Back also cancels. Playback stops when opening confirmation. A second tap at the old Delete button's position selects Keep recording, rather than confirming accidentally.

Deletion removes only that recording's watch PCM file and its timestamp/sync sidecars. Any existing phone WAV and app catalog remain untouched. An unconfirmed phone copy is called out before deletion; explicitly deleting an unsynced memo can lose its only copy. The user can delete unsynced recordings to free watch storage. There is no bulk-delete action or automatic deletion as part of flashing this update.

After confirming, the list refreshes, empty pages collapse and deleting the last memo shows the empty library. A new memo can then be recorded. Deletion is keyed by stable recording ID, not a changing page position. A failed PCM removal preserves the catalog entry for retry. Deletion during an asynchronous save is rejected for retry. Pending playback loads are cancelled and the cached playback buffer is invalidated after successful removal.

The boot catalog scans complete `.pcm` files, so a deleted recording does not reappear after restart. If a phone was downloading the removed memo, that transfer may fail; completed phone copies are never deleted by this operation. No Android APK update is required.

Verification: the real file reader/deletion functions pass native ASan/UBSan checks for invalid IDs, capacity/hash failures, failed unlink, catalog retention on failure, exact PCM/sidecar removal and preservation of a separate copy. The actual C++ UI tests cover cancellation, repeated taps, failed deletion, playback stopping, selected-ID removal, page collapse, deleting the last entry and starting a new recording. Files used in these tests are temporary fixtures; existing watch recordings are not deleted by the tests.

Build uses ESP-IDF 5.2.8, main startup stack 8 KiB and `NAARI_HARDWARE_SELFTEST=OFF`. Only the application partition is flashed. Firmware/UI/storage tests and final device boot are checked before reporting completion.

Final device check: watch v8.5 booted successfully with all six existing recordings still present. Hardware log: `/tmp/naari-watch-v85-boot.log`. Firmware SHA-256: `158f8aa2e87fecd4d9380f3f94157ef5a4c35946be5bf7d887fb07f1830848c3`. No real recording was deleted by automated tests.
