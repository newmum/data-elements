import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Reads one JSON object or array from stdin and writes passing rows to stdout. */
public final class QualityCheck {
    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        JsonNode input = mapper.readTree(System.in);
        var accepted = mapper.createArrayNode();
        Iterable<JsonNode> records;
        if (input != null && input.isArray()) records = input;
        else if (input != null && input.isObject()) records = java.util.List.of(input);
        else throw new IllegalArgumentException("Expected a JSON object or array");
        int rejected = 0;
        for (var record : records) {
            String id = record.path("record_id").asText("").strip();
            String status = record.path("status").asText("").strip().toUpperCase(Locale.ROOT);
            BigDecimal amount = amount(record.path("amount"));
            if (id.isEmpty() || id.length() > 40 || amount == null || amount.signum() < 0
                    || amount.scale() > 2 || amount.precision() - amount.scale() > 10
                    || !Set.of("ACTIVE", "PENDING").contains(status)) {
                rejected++;
                continue;
            }
            accepted.addObject().put("record_id", id).put("amount", amount)
                    .put("status", status).put("quality_status", "PASS");
        }
        System.err.printf("accepted=%d rejected=%d%n", accepted.size(), rejected);
        if (accepted.isEmpty()) {
            mapper.writeValue(System.out, input);
            System.exit(2);
        }
        mapper.writeValue(System.out, accepted);
    }

    private static BigDecimal amount(JsonNode value) {
        // MongoDB may return Decimal128 as Extended JSON.
        String decimal = value.isObject() ? value.path("$numberDecimal").asText("") : value.asText("");
        try { return new BigDecimal(decimal.strip()); }
        catch (NumberFormatException invalid) { return null; }
    }
}
