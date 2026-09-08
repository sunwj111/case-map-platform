package com.casemap.hierarchy.produce;

import java.util.List;

public record ReviewQueueResult(
        int total,
        int pending,
        int confirmed,
        int discarded,
        int published,
        List<ReviewItem> items
) {
}
