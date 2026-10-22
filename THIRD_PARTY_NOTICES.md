# Third-party notices

RealStreamer is built with the following third-party components. This list is provided for attribution and convenience; it is not legal advice. The license texts shipped by upstream projects govern.

## Vendored UVC camera engine

`app/libs/AndroidUSBCamera-2.3.8.aar` is a vendored build of
[AndroidUSBCamera](https://github.com/jiangdongguo/AndroidUSBCamera) by Jiangdongguo, licensed under the **Apache License 2.0**. A copy of its license is at `app/libs/AndroidUSBCamera-LICENSE.txt`.

The AAR was modified for this project: the `x86` and `x86_64` native library directories were removed because the app targets ARM devices only. No Java/Kotlin classes or other resources were changed.

The AAR bundles native components with their own licenses:

| Component | License | Upstream |
|---|---|---|
| UVCCamera native layer (saki4510t / serenegiant) | Apache-2.0 | https://github.com/saki4510t/UVCCamera |
| libuvc (Ken Tossell et al.; Android modifications by saki4510t) | BSD-3-Clause | https://github.com/ktossell/libuvc |
| libusb | LGPL-2.1 | https://libusb.info |
| libjpeg-turbo 1.5.0 | BSD-3-Clause / IJG | https://libjpeg-turbo.org |
| `libTxtOverlay` / `org.easydarwin.sw` (bundled by the upstream AAR; not used by RealStreamer) | See upstream | https://github.com/EasyDarwin/EasyDarwin |

The Java/Kotlin UVC wrapper (`com.serenegiant.usb.*`, `com.jiangdg.*`) originates from the AndroidUSBCamera project and the UVCCamera project above.

## Android and Kotlin libraries

| Component | License |
|---|---|
| AndroidX / Jetpack Compose / Material 3 | Apache-2.0 |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| OkHttp | Apache-2.0 |
| Android Gradle Plugin, Gradle | Apache-2.0 |

## Protocol and media work in this repository

The RTMP client (`core/.../rtmp/`), FLV/AMF0/H.264/AAC helpers, GL compositor, overlay renderer, capture orchestration, fan-out pipeline, and UI are original to this project. The RTMP protocol is implemented from the public RTMP 1.0 specification and the FLV/AAC/H.264 container specifications; no code was copied from librtmp, FFmpeg, or other clients.

## Fonts and artwork

The UI bundles the following fonts, all under the **SIL Open Font License 1.1**:

| Font | Weights used | Copyright | License text |
|---|---|---|---|
| Barlow | Regular, Medium, SemiBold | The Barlow Project Authors | `licenses/OFL-Barlow.txt` |
| Barlow Condensed | SemiBold, Bold | The Barlow Project Authors | `licenses/OFL-Barlow.txt` |
| IBM Plex Mono | Regular, Medium | IBM Corp. | `licenses/OFL-IBMPlexMono.txt` |

Font files live in `app/src/main/res/font/`. The adaptive app icon and notification glyph are vector artwork created for this project. The reference design chart (`New Project.png`) was supplied by the project owner.

## Full license texts

Where a dependency is consumed from Maven, its license is available in the published artifact or its project repository. The UVC AAR's Apache-2.0 text is included locally. For LGPL components (libusb), the upstream source and license are available at the links above.
