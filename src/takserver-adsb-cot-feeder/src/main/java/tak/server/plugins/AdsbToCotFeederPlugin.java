package tak.server.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.takserver.plugin.annotation.TakServerPlugin;
import com.bbn.takserver.plugin.messages.MessageSenderBase;

/**
 * Plugin skeleton that converts ADS-B tracks to Cursor-on-Target messages using
 * the adsbcot project. This class contains only stub logic.
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

    @Override
    public void start() throws Exception {
        logger.info("ADSB to CoT Feeder started. source={}, feed={}", adsbSource, feedUuid);
        // TODO: invoke adsbcot to fetch ADS-B data and convert to CoT
    }

    @Override
    public void stop() throws Exception {
        logger.info("ADSB to CoT Feeder stopped.");
    }

    public void setAdsbSource(String adsbSource) {
        this.adsbSource = adsbSource;
    }

    public void setFeedUuid(String feedUuid) {
        this.feedUuid = feedUuid;
    }
}
