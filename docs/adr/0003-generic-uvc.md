# ADR-0003: Generic UVC via an Apache-2.0 USB stack, no vendor SDKs

- **Status:** Accepted
- **Date:** 2026-10-22

## Context

The primary external camera is a DJI Action 4, which exposes a standard UVC webcam mode. A vendor SDK would add account/terms constraints, be specific to DJI, and not help with other cameras. Android's USB host API does not support isochronous transfers directly, so a native UVC stack (libusb/libuvc) is required.

## Decision

Vendor AndroidUSBCamera 2.3.8 (Apache-2.0), which bundles a saki4510t UVCCamera JNI layer, libusb (LGPL-2.1), libuvc (BSD), and libjpeg-turbo (BSD/IJG). Drive `USBMonitor.openDevice` + `UVCCamera` directly, with our own USB permission receiver and hotplug handling. Enumerate only devices with a UVC interface; select MJPEG/YUYV sizes with fallbacks. No VID/PID or vendor code.

## Consequences

- Works with any standard UVC camera, including action cameras in webcam mode.
- We must manage native library ABIs (arm64/armv7), USB permissions, producer lifetimes, and camera-specific format quirks.
- Audio is not captured from UVC; the phone mic is used.
- The vendored AAR is a maintenance point; it is stripped of x86 ABIs and included with its license.

## Alternatives rejected

- **DJI SDK / vendor app:** violates the "no vendor hacks" requirement and only covers one camera.
- **Writing a UVC stack from scratch:** large native effort (isochronous USB, MJPEG decode) with no benefit over the proven Apache-2.0 stack.
- **Camera2 external camera support:** only covers cameras exposed as Android camera devices, not arbitrary UVC.
