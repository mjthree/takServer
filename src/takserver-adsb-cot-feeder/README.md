# ADSB to CoT Feeder Plugin

This plugin demonstrates how to feed TAK Server data channels with Cursor-on-Target (CoT) messages generated from ADS-B tracks.  
ADSB tracks may be obtained from a local SDR receiver or via the [adsb.lol](https://adsb.lol) API and converted to CoT using [adsbcot](https://github.com/snstac/adsbcot).

## Build
From this directory (`src/takserver-adsb-cot-feeder`):

```bash
./gradlew clean shadowJar
```

The resulting JAR is written to `build/libs/`.

## Install into TAK Server
1. Create a `lib` directory under your TAK Server execution directory and copy the generated JAR there.
2. Create a `conf/plugins` directory under the execution directory.
3. Copy `tak.server.plugins.AdsbToCotFeederPlugin.yaml` into the `conf/plugins` directory and edit as needed.
4. Start the TAK Server Messaging, API, and Plugin processes ensuring that the `lib` directory is on the plugin classpath.

Once started, the plugin periodically queries the configured ADS-B source, converts the tracks to CoT, and publishes the results to the configured data feed. Each CoT event is also broadcast over UDP to the `targetAddress`/`targetPort` specified in the plugin configuration so external systems can receive the data directly.

