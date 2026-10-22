package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Layout the expanded native graph, including processors inside one DSL node. */
public final class NativeCanvasLayout {
    static final double LEFT = 400;
    static final double TOP = 120;
    static final double COLUMN_GAP = 680;
    static final double ROW_GAP = 300;
    static final double CARD_WIDTH = 350;
    static final double CARD_HEIGHT = 130;

    private NativeCanvasLayout() { }

    public record Point(double x, double y) { }
    public record Link(String id, String source, String target, List<String> relationships) { }
    public record Route(List<Point> bends, int labelIndex) { }

    public static List<Link> links(JsonNode connections) {
        List<Link> result = new ArrayList<>();
        for (JsonNode entity : connections) {
            JsonNode c = entity.path("component");
            List<String> relationships = new ArrayList<>();
            c.path("selectedRelationships").forEach(r -> relationships.add(r.asText()));
            result.add(new Link(entity.path("id").asText(), c.path("source").path("id").asText(),
                    c.path("destination").path("id").asText(), relationships));
        }
        result.sort(Comparator.comparing(Link::id));
        return result;
    }

    public static Map<String, Point> positions(JsonNode processors, List<Link> links) {
        List<JsonNode> ordered = new ArrayList<>();
        processors.forEach(ordered::add);
        ordered.sort(Comparator.comparingDouble((JsonNode p) -> p.path("component").path("position").path("y").asDouble())
                .thenComparingDouble(p -> p.path("component").path("position").path("x").asDouble())
                .thenComparing(p -> p.path("component").path("name").asText())
                .thenComparing(p -> p.path("id").asText()));
        Set<String> remaining = new LinkedHashSet<>();
        ordered.forEach(p -> remaining.add(p.path("id").asText()));
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        for (Link link : links) {
            if (!link.source().equals(link.target()) && remaining.contains(link.source())
                    && remaining.contains(link.target()) && !isErrorLink(link)) {
                parents.computeIfAbsent(link.target(), key -> new LinkedHashSet<>()).add(link.source());
            }
        }
        Map<String, Point> result = new LinkedHashMap<>();
        while (!remaining.isEmpty()) {
            String next = remaining.stream().filter(id -> parents.getOrDefault(id, Set.of()).stream()
                    .noneMatch(remaining::contains)).findFirst().orElse(remaining.iterator().next());
            int index = result.size();
            int row = index / 2;
            int column = row % 2 == 0 ? index % 2 : 1 - index % 2;
            result.put(next, new Point(LEFT + column * COLUMN_GAP, TOP + row * ROW_GAP));
            remaining.remove(next);
        }
        return result;
    }

    public static boolean isErrorLink(Link link) {
        return !link.relationships().isEmpty() && link.relationships().stream()
                .allMatch(r -> "failure".equals(r) || "retry".equals(r));
    }

    /** Retained invalid queues live outside the main two-column path. */
    public static Map<String, Point> retentionPositions(JsonNode funnels, List<Link> links, Map<String, Point> processors) {
        Set<String> ids = new LinkedHashSet<>();
        funnels.forEach(funnel -> ids.add(funnel.path("id").asText()));
        double right = processors.values().stream().mapToDouble(p -> p.x() + CARD_WIDTH).max().orElse(LEFT + CARD_WIDTH);
        Map<Double, Integer> slots = new LinkedHashMap<>();
        Map<String, Point> result = new LinkedHashMap<>();
        links.stream().filter(link -> ids.contains(link.target()) && processors.containsKey(link.source())
                        && link.relationships().contains("invalid"))
                .sorted(Comparator.comparingDouble((Link link) -> processors.get(link.source()).y())
                        .thenComparingDouble(link -> processors.get(link.source()).x()).thenComparing(Link::id))
                .forEach(link -> {
                    if (result.containsKey(link.target())) return;
                    Point source = processors.get(link.source());
                    int slot = slots.merge(source.y(), 1, Integer::sum) - 1;
                    result.put(link.target(), invalidFunnel(source, right, slot));
                });
        return result;
    }

    public static Point invalidFunnel(Point source, double rightEdge, int slot) {
        return new Point(rightEdge + 420 + slot * 340, source.y() - 200);
    }

    /** Put the label on a clear outer segment, above failure/retry and beside success. */
    public static Route invalidRoute(Point source, Point funnel) {
        double end = funnel.x() - 80, start = end - 260, y = source.y() - 100;
        List<Point> bends = new ArrayList<>();
        double departure = source.x() + CARD_WIDTH + 80;
        if (departure < start - 1) bends.add(new Point(departure, y));
        bends.add(new Point(start, y));
        bends.add(new Point(end, y));
        return new Route(List.copyOf(bends), bends.size() - 1);
    }

    public static Route route(Link link, Map<String, Point> positions, int lane) {
        Point source = positions.get(link.source());
        Point target = positions.get(link.target());
        if (link.source().equals(link.target())) {
            double side = source.x() == LEFT ? source.x() - 180 : source.x() + CARD_WIDTH + 180;
            return new Route(List.of(new Point(side, source.y() + 35), new Point(side, source.y() + 95)), 0);
        }
        List<String> order = new ArrayList<>(positions.keySet());
        if (order.indexOf(link.target()) == order.indexOf(link.source()) + 1) {
            return new Route(List.of(), 0);
        }
        // Branches and back edges use separate outer lanes instead of crossing cards.
        double offset = 30 + (lane % 8) * 12;
        double side = LEFT + COLUMN_GAP + CARD_WIDTH + 380 + lane * 28;
        double startY = source.y() + CARD_HEIGHT + offset;
        double endY = target.y() - offset;
        return new Route(List.of(new Point(source.x() + CARD_WIDTH / 2, startY),
                new Point(side, startY), new Point(side, endY),
                new Point(target.x() + CARD_WIDTH / 2, endY)), 2);
    }
}
