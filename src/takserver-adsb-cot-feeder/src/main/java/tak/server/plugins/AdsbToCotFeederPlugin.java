package tak.server.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.takserver.plugin.annotation.TakServerPlugin;
import com.bbn.takserver.plugin.messages.MessageSenderBase;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import atakmap.commoncommo.protobuf.v1.MessageOuterClass.Message;

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

    /** UDP address to send generated CoT messages to. */
    private String targetAddress = "127.0.0.1";

    /** UDP port to send generated CoT messages to. */
    private int targetPort = 8089;

    /** Time in seconds between ADS-B fetches. */
    private int intervalSeconds = 30;

    private ScheduledExecutorService executor;

    @Override
    public void start() throws Exception {
        logger.info("ADSB to CoT Feeder started. source={}, feed={}, target={}:{} interval={}s", 
                adsbSource, feedUuid, targetAddress, targetPort, intervalSeconds);

        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this::fetchAndPublish, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void stop() throws Exception {
        if (executor != null) {
            executor.shutdownNow();
        }
        logger.info("ADSB to CoT Feeder stopped.");
    }

    public void setAdsbSource(String adsbSource) {
        this.adsbSource = adsbSource;
    }

    public void setFeedUuid(String feedUuid) {
        this.feedUuid = feedUuid;
    }

    public void setTargetAddress(String targetAddress) {
        this.targetAddress = targetAddress;
    }

    public void setTargetPort(int targetPort) {
        this.targetPort = targetPort;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    private void fetchAndPublish() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(adsbSource))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode aircraft = root.path("aircraft");
            if (aircraft.isArray()) {
                for (JsonNode ac : aircraft) {
                    publishAircraft(ac);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch ADS-B data", e);
        }
    }

    private void publishAircraft(JsonNode ac) {
        try {
            String uid = "adsb-" + ac.path("hex").asText();
            double lat = ac.path("lat").asDouble();
            double lon = ac.path("lon").asDouble();
            double alt = ac.path("alt_baro").asDouble(0.0);
            double course = ac.path("track").asDouble(0.0);
            double speed = ac.path("vel").asDouble(0.0);
            String callsign = ac.path("flight").asText("");

            String cot = buildCot(uid, lat, lon, alt, course, speed, callsign);

            // send over UDP
            byte[] data = cot.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(targetAddress), targetPort);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
            }

            // send to TAK Server data feed
            Message msg = getConverter().cotStringToDataMessage(cot, null, uid);
            send(msg, feedUuid);

        } catch (Exception e) {
            logger.warn("Failed to publish aircraft", e);
        }
    }

    private String buildCot(String uid, double lat, double lon, double alt,
            double course, double speed, String callsign) {
        Instant now = Instant.now();
        Instant stale = now.plusSeconds(60);
        String time = DateTimeFormatter.ISO_INSTANT.format(now);
        String staleTime = DateTimeFormatter.ISO_INSTANT.format(stale);

        return String.format("<event version=\"2.0\" uid=\"%s\" type=\"a-f-A-M-F-Q\" how=\"m-g\" " +
                        "time=\"%s\" start=\"%s\" stale=\"%s\"><point lat=\"%f\" lon=\"%f\" hae=\"%f\" " +
                        "ce=\"9999999.0\" le=\"9999999.0\"/><detail><contact callsign=\"%s\"/><track speed=\"%f\" course=\"%f\"/></detail></event>",
                uid, time, time, staleTime, lat, lon, alt, callsign, speed, course);
    }
}
