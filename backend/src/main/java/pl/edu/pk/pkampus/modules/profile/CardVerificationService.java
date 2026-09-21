package pl.edu.pk.pkampus.modules.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

@Service
public class CardVerificationService {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private static final List<ColorEntry> PALETTE = List.of(
            new ColorEntry("#2563EB", "Niebieski"),
            new ColorEntry("#16A34A", "Zielony"),
            new ColorEntry("#DC2626", "Czerwony"),
            new ColorEntry("#CA8A04", "Żółty"),
            new ColorEntry("#9333EA", "Fioletowy"),
            new ColorEntry("#EA580C", "Pomarańczowy"),
            new ColorEntry("#0891B2", "Turkusowy")
    );

    private final String hmacSecret;
    private final Clock clock;

    public CardVerificationService(
            @Value("${jwt.secret}") String hmacSecret,
            Clock clock
    ) {
        this.hmacSecret = hmacSecret;
        this.clock = clock;
    }

    public CardDayToken todaysToken() {
        return tokenForDate(LocalDate.now(clock.withZone(WARSAW)));
    }

    public CardDayToken tokenForDate(LocalDate date) {
        String dateKey = date.toString(); // yyyy-MM-dd
        byte[] digest = hmacSha256(hmacSecret, dateKey);
        String fullHex = HexFormat.of().formatHex(digest).toUpperCase();
        String dayCode = fullHex.substring(0, 6);
        int colorIndex = Byte.toUnsignedInt(digest[digest.length - 1]) % PALETTE.size();
        ColorEntry color = PALETTE.get(colorIndex);
        return new CardDayToken(dayCode, color.hex(), color.name(), dateKey);
    }

    private static byte[] hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive daily card verification token", e);
        }
    }

    private record ColorEntry(String hex, String name) {
    }
}
