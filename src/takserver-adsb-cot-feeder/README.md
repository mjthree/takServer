# ADSB to CoT Feeder Plugin

This plugin demonstrates how to feed TAK Server data channels with Cursor-on-Target (CoT) messages generated from ADS-B tracks. ADSB tracks may be obtained from a local SDR receiver or via the [adsb.lol](https://adsb.lol) API and converted to CoT using [adsbcot](https://github.com/snstac/adsbcot).

The plugin expects a running `adsbcot` instance that outputs CoT messages over a TCP socket. Optionally, the plugin can launch `adsbcot` itself using the configured command.

## Configuration
Edit `tak.server.plugins.AdsbToCotFeederPlugin.yaml`:

```yaml
adsbSource: "adsb.lol"       # "local" for an SDR or "adsb.lol" for remote data
feedUuid: "adsb-cot-feed"    # Data feed UUID
adsbCotHost: "localhost"     # Host where adsbcot publishes CoT
adsbCotPort: 5000            # Port used by adsbcot
adsbCotCommand: ""           # Optional command to launch adsbcot
system: {archive: true}
```

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

Once started, the plugin creates the configured data feed (if needed), connects to the `adsbcot` socket, converts received CoT XML into protobuf messages, and publishes them to the selected feed.
