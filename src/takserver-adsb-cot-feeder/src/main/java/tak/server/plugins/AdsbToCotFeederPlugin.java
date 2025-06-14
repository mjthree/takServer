package tak.server.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import atakmap.commoncommo.protobuf.v1.MessageOuterClass.Message;

import com.bbn.takserver.plugin.annotation.TakServerPlugin;
import com.bbn.takserver.plugin.messages.MessageSenderBase;
import tak.server.plugins.PluginDataFeedApi;

/**
 * Plugin that converts ADS-B tracks to Cursor-on-Target messages using the
 * adsbcot project. It connects to a running adsbcot instance that outputs CoT
 * messages over a TCP socket and forwards those messages to a TAK Server data
 * feed.
 */
@TakServerPlugin(
    name = "ADSB to CoT Feeder",
    description = "Feeds CoT tracks generated from ADS-B data"
)
public class AdsbToCotFeederPlugin extends MessageSenderBase {

    private static final Logger logger = LoggerFactory.getLogger(AdsbToCotFeederPlugin.class);

    /**
     * ADSB source. Can be "local" for SDR input or "adsb.lol" for the API.
     */
    private String adsbSource = "adsb.lol";

    /** UUID of the data feed to publish CoT messages to. */
    private String feedUuid = "adsb-cot-feed";

    /** Host running the adsbcot service. */
    private String adsbCotHost = "localhost";

    /** Port where adsbcot publishes CoT messages. */
    private int adsbCotPort = 5000;

    /** Optional command to launch adsbcot if not already running. */
    private String adsbCotCommand = "";

    private ExecutorService receiver;
    private volatile boolean running;
    private Process adsbCotProcess;
    private Socket socket;

    public AdsbToCotFeederPlugin() {
        if (config.containsProperty("adsbSource")) {
            adsbSource = config.getProperty("adsbSource").toString();
        }
        if (config.containsProperty("feedUuid")) {
            feedUuid = config.getProperty("feedUuid").toString();
        }
        if (config.containsProperty("adsbCotHost")) {
            adsbCotHost = config.getProperty("adsbCotHost").toString();
        }
        if (config.containsProperty("adsbCotPort")) {
            adsbCotPort = (int) config.getProperty("adsbCotPort");
        }
        if (config.containsProperty("adsbCotCommand")) {
            adsbCotCommand = config.getProperty("adsbCotCommand").toString();
        }
    }

    @Override
    public void start() throws Exception {
        logger.info("ADSB to CoT Feeder starting. source={}, feed={}", adsbSource, feedUuid);

        List<String> tags = new ArrayList<>();
        tags.add("adsb");
        try {
            PluginDataFeedApi api = getPluginDataFeedApi();
            api.create(feedUuid, "adsb-feed", tags);
        } catch (Exception e) {
            logger.debug("Data feed {} may already exist: {}", feedUuid, e.getMessage());
        }

        if (!adsbCotCommand.isEmpty()) {
            logger.info("Launching adsbcot: {}", adsbCotCommand);
            adsbCotProcess = new ProcessBuilder(adsbCotCommand.split(" ")).start();
        }

        receiver = Executors.newSingleThreadExecutor();
        running = true;
        receiver.submit(this::receiveLoop);
    }

    private void receiveLoop() {
        try {
            socket = new Socket(adsbCotHost, adsbCotPort);
            InputStream is = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    Message m = getConverter().cotStringToDataMessage(line, null, "adsb");
                    send(m, feedUuid);
                } catch (Exception e) {
                    logger.warn("Failed to process CoT message", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Error reading from adsbcot", e);
            }
        }
    }

    @Override
    public void stop() throws Exception {
        running = false;
        if (receiver != null) {
            receiver.shutdownNow();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
        if (adsbCotProcess != null) {
            adsbCotProcess.destroy();
        }
        logger.info("ADSB to CoT Feeder stopped.");
    }

    public void setAdsbSource(String adsbSource) {
        this.adsbSource = adsbSource;
    }

    public void setFeedUuid(String feedUuid) {
        this.feedUuid = feedUuid;
    }
}
