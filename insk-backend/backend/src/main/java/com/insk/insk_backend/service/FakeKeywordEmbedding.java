package com.insk.insk_backend.service;

import java.util.ArrayList;
import java.util.List;

public class FakeKeywordEmbedding {

    public static List<Double> make(String keyword) {
        List<Double> v = new ArrayList<>();
        int base = Math.abs(keyword.hashCode() % 100) + 1;
        for (int i = 0; i < 256; i++) {
            v.add((base + i) / 300.0);
        }
        return v;
    }
}
