# Fall-detection review: suban244/FallDetection vs Naari

Reviewed 18 October 2026 at upstream commit `784304d141fcc6a5e81a2fb9aa9753c9edcc15f0`.

**Verdict:** useful research/reference material, but not a working drop-in replacement for Naari. There is no evidence that transplanting its code or weights would fix either missed falls or false alarms on this wristwatch. No Naari firmware was changed or flashed during this review.

## Correction to the current Naari status

Naari's sensor initialization/read-back and approximately 250 Hz acquisition were verified on the physical watch. Synthetic detector tests passed. Those results establish that the code runs and the sensor path is alive; they do not establish real fall sensitivity or an acceptable false-alarm rate. The user's repeated failures and unexpected activations are evidence that the present detector is not ready to be described as reliable.

The current implementation in `firmware/main/safety/fall_detector.cpp` is a hand-written threshold state machine, not a trained fall classifier:

- An impact of at least 3 g plus angular speed of at least 120 degrees/second within 300 ms can enter the post-impact candidate state without free-fall. An ordinary sharp wrist motion/bump followed by rest can satisfy these same conditions.
- The other path requires acceleration below 0.45 g for 80 ms and a subsequent impact of at least 3 g. Lower-impact or soft-surface falls can fail this rule.
- Confirmation requires 2.5 continuous seconds in a narrow acceleration band and angular speed below 45 degrees/second. Movement after a real fall can repeatedly reset that confirmation.
- A detected event creates a 60-second cooldown, which also affects repeated tests. There is no wear-detection signal to distinguish a dropped unworn watch from a wearer fall.
- Gyroscope magnitude measures angular speed; it is not an orientation/posture estimate. Higher sample rate helps observe motion, but does not solve this classification ambiguity.

I replayed two synthetic traces through the actual current C++ detector with ASan/UBSan:

| Trace | Current output | What it demonstrates |
| --- | --- | --- |
| Rest → 180 deg/s motion → 3.2 g impact → quiet 1 g | Alarm | The rule cannot distinguish this constructed bump/rest trace from a fall candidate. This is not proof that a particular real wrist gesture has these readings. |
| Rest → 0.2 g low acceleration → 2.5 g impact → quiet 1 g | No alarm | The hard 3 g gate rejects this constructed lower-impact fall-like sequence. |

Saved boot logs contain live samples and candidate phases but not complete raw event windows. They do not establish the cause of every reported activation. Manual button/UI SOS paths also exist. If the monitoring switch itself changes while off, that is a separate settings/input issue and must be distinguished from an unexpected SOS alarm.

## What the linked repository contains

[README](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/README.md) describes MPU6050 + Raspberry Pi Pico + SIM900A, CNN/LSTM classification, three-second windows at ten samples/second, and approximately 600 simulated samples from three people. Naari uses QMI8658 + ESP32-S3, with different units/ranges, firmware APIs and acquisition timing. Wrist placement is not sufficiently specified/validated by the repository documentation to assume transfer to this watch.

There are separate implementation paths; the README's ML description does not mean its live microcontroller code deploys that classifier correctly.

### 1. Pico C++ inference is not connected to six-axis fall data

In [`pico/TFLModel.cpp`](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/pico/TFLModel.cpp), `ReadMPU(...)` is commented out. `loop()` generates an input value from an inference counter, quantizes that scalar and writes only `input->data.int8[0]`. Its output is handed to [`output_handeler.cpp`](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/pico/output_handeler.cpp), which changes LED brightness. This is demonstrator-style inference wiring, not a six-axis fall-classification pipeline. `combined.cpp` reads/prints accelerometer values separately but does not pass that array into the model.

Therefore flashing/porting that embedded path as-is would not provide the described fall detector, regardless of whether a model binary is present.

### 2. The MicroPython application uses a rule detector

[`micropython/main.py`](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/micropython/main.py) imports `RuleBased.FallDetector`, runs a nominal 20 Hz loop and supplies device-specific calibration offsets. It does not invoke the desktop CNN/LSTM. Its GSM destination is hard-coded, so the action layer must never be transplanted into a test on Naari.

[`RuleBased.py`](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/micropython/RuleBased.py) has additional problems:

- The acceleration thresholds 6 and 12 operate on m/s² in its driver, approximately 0.61 g and 1.22 g. Feeding Naari's g values directly into those thresholds would change the behavior dramatically.
- `angleChange` is a gyroscope magnitude, not a measured change in orientation.
- Its waiting periods are sample counts. Thirty samples are nominally 1.5 seconds at the application's 20 Hz rate; copying that count into Naari's 250 Hz loop would mean about 0.12 seconds.
- The final stillness condition is checked at the end of the count, not maintained continuously throughout the interval.
- Once `self.fall` becomes true, it is never reset. I reproduced that the next idle call still returns true. The application's `messageSent` latch then suppresses future alert sends until reset, rather than providing a reusable incident lifecycle.

This path exchanges one collection of unvalidated thresholds for another.

### 3. Desktop inference has temporal-input problems

[`inference.py`](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/inference.py) uses desktop PyTorch, serial IO and a Turtle GUI. It loads `model3.pt`. It is not ESP-IDF firmware.

After filling its 30-frame queue, it removes the last element and inserts each new frame at the beginning. Starting with frames 0…29, adding frame 30 produces `[30, 0, 1, …, 28]`, not the chronological window `[1, 2, …, 30]`. I reproduced that queue update independently. The CNN path also reshapes time-major six-axis rows directly into a channel-major input without a time/channel transpose, so preprocessing consistency must be established before trusting its predictions.

Naari's 30 samples at 250 Hz span 0.12 seconds; this repo's stated 30 samples at 10 Hz span three seconds. A valid port needs a defined sample rate, filtering/resampling, units, axes, calibration and tensor layout. Model weights cannot fix a mismatched input pipeline.

### 4. Validation is insufficient and does not reproduce the desktop model

The committed [training notebook](https://github.com/suban244/FallDetection/blob/784304d141fcc6a5e81a2fb9aa9753c9edcc15f0/colab/pytorch_base.ipynb) shows a binary LSTM with 10 hidden units, while desktop inference uses a different three-class CNN/LSTM architecture. Its recorded binary validation run contains only 26 examples: matrix `[[12,2],[1,11]]`, with reported accuracy 88.46% and recall 91.67%. These are that notebook run's results, not measurements of the deployed desktop CNN or Naari.

The notebook randomly splits files across train/validation, rather than holding out entire people. It does not demonstrate independent-user wrist performance or hours-long false-alarm rates. `split.py` additionally samples multiple windows from a source recording; related windows must be grouped together during any evaluation to avoid leakage. The committed notebook does not demonstrate whether those particular split windows were used, so leakage is a risk to check, not a proven explanation of its scores.

## What is worth reusing

The useful direction is time-window classification using all six axes, labelled everyday activities and falls, and quantitative evaluation. The repo can inform data logging and experimentation. Its implementation and weights need independent correction and validation before integration.

A practical sequence for Naari is:

1. Capture timestamped raw accelerometer/gyro windows from the actual watch around reported misses and unexpected activations. Record the trigger source and detector transitions. Use an explicit no-dispatch test mode for collection.
2. Label wear location, worn/unworn state where known, activity and outcome. Include ordinary walking, sitting down, lying down, rapid hand motion, wrist bumps and device handling. Use controlled supervised fall/impact evaluation, not unsupervised drops of a person or the watch.
3. Verify physical units, stationary bias, sensor clipping, sample freshness and timing. Replay these recordings offline against the current heuristic to locate precisely which gate failed.
4. Compare an improved state machine and a small temporal classifier on the same held-out recordings. Keep people and recording sessions separate between training and testing. Measure missed falls, false alarms per hour, detection delay, energy and inference latency.
5. Only then deploy a measured detector with calibrated confidence/event handling into the existing watch UI, countdown and phone/LTE request system. Keep manual SOS available independently.

**Recommendation:** do not replace Naari with this repository or claim that “ML” automatically makes it reliable. First establish an actual-watch dataset and reproducible evaluation. Healthy IMU telemetry, synthetic passing tests and an attractive confusion matrix are each useful evidence of something narrower than dependable real-world fall detection.
