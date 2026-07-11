package com.kiastore.service;

import com.kiastore.dao.PartDao;
import com.kiastore.model.Part;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class PartService {

    private final PartDao partDao;

    public PartService(PartDao partDao) {
        this.partDao = partDao;
    }

    public List<Part> all() {
        return partDao.findAll();
    }

    public List<Part> lowStock() {
        return partDao.findLowStock();
    }

    public Optional<Part> findById(int id) {
        return partDao.findById(id);
    }

    /**
     * Saves a part, auto-generating the full name based on structured attributes.
     */
    public Part save(Part p) {
        // Auto-generate full_name: part_type + location + car_name + car_model + manufacturer
        // Example: "تيل فرامل أمامي سيراتو 2010 Bosch"
        StringBuilder sb = new StringBuilder();
        if (p.getPartType() != null && !p.getPartType().isBlank()) sb.append(p.getPartType().trim()).append(" ");
        if (p.getLocation() != null && !p.getLocation().isBlank()) sb.append(p.getLocation().trim()).append(" ");
        if (p.getCarName() != null && !p.getCarName().isBlank()) sb.append(p.getCarName().trim()).append(" ");
        if (p.getCarModel() != null && !p.getCarModel().isBlank()) sb.append(p.getCarModel().trim()).append(" ");
        if (p.getManufacturer() != null && !p.getManufacturer().isBlank()) sb.append(p.getManufacturer().trim());
        
        p.setFullName(sb.toString().trim());

        // Normalize part number (e.g. F3-5960 -> F35960)
        if (p.getPartNumber() != null) {
            p.setPartNumberNormalized(normalizePartNumber(p.getPartNumber()));
        }

        if (p.getId() > 0) {
            partDao.update(p);
            return p;
        } else {
            return partDao.insert(p);
        }
    }

    public boolean delete(int id) {
        return partDao.delete(id);
    }

    public int countAll() {
        return partDao.countAll();
    }

    public double getInventoryValue() {
        return partDao.getInventoryValue();
    }

    /**
     * Helper to normalize Arabic letters to make searching diacritics/spelling-independent.
     */
    private String normalizeArabic(String text) {
        if (text == null) return "";
        String out = text.toLowerCase().trim();
        // Remove diacritics
        out = out.replaceAll("[\\u064B-\\u065F]", "");
        // Normalize Alif
        out = out.replaceAll("[أإآ]", "ا");
        // Normalize Ta Marbouta
        out = out.replaceAll("ة", "ه");
        // Normalize Ya
        out = out.replaceAll("ى", "ي");
        return out;
    }

    /**
     * Helper to normalize part numbers by removing spaces and dashes.
     */
    private String normalizePartNumber(String partNo) {
        if (partNo == null) return "";
        return partNo.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    /**
     * Smart fuzzy search with ranking:
     * best match -> in stock -> most sold -> last used
     */
    public List<Part> searchSmart(String query) {
        List<Part> allParts = partDao.findAll().stream()
                .filter(Part::isActive)
                .collect(Collectors.toList());

        if (query == null || query.isBlank()) {
            return allParts;
        }

        // Tokenize query
        String normalizedQuery = normalizeArabic(query);
        String[] queryTokens = normalizedQuery.split("\\s+");
        
        // Load sales count and last update timestamps to help ranking
        Map<Integer, Integer> salesCount = getSalesCountMap();

        List<SearchResult> results = new ArrayList<>();

        for (Part p : allParts) {
            String normName = normalizeArabic(p.getFullName());
            String normCar = normalizeArabic(p.getCarName() + " " + p.getCarModel());
            String normCompat = normalizeArabic(p.getCompatibleCars());
            String normPartNo = normalizePartNumber(p.getPartNumber());
            String normInternal = normalizePartNumber(p.getInternalCode());
            String normBarcode = normalizePartNumber(p.getBarcode());
            String normManufacturer = normalizeArabic(p.getManufacturer());

            boolean matchAll = true;
            int score = 0;

            for (String token : queryTokens) {
                boolean tokenMatched = false;
                
                // Match in full name
                if (normName.contains(token)) {
                    tokenMatched = true;
                    score += 50;
                    // Extra points for exact prefix match
                    if (normName.startsWith(token)) score += 20;
                }
                
                // Match in car make/model
                if (normCar.contains(token)) {
                    tokenMatched = true;
                    score += 30;
                }
                
                // Match in compatible cars JSON
                if (normCompat.contains(token)) {
                    tokenMatched = true;
                    score += 15;
                }

                // Match in manufacturer
                if (normManufacturer.contains(token)) {
                    tokenMatched = true;
                    score += 15;
                }

                // Match in part numbers or codes (normalized)
                String normalizedToken = normalizePartNumber(token);
                if (!normalizedToken.isEmpty()) {
                    if (normPartNo.contains(normalizedToken) || normInternal.contains(normalizedToken) || normBarcode.contains(normalizedToken)) {
                        tokenMatched = true;
                        score += 40;
                        if (normPartNo.equals(normalizedToken) || normInternal.equals(normalizedToken)) score += 30;
                    }
                }

                if (!tokenMatched) {
                    matchAll = false;
                    break;
                }
            }

            if (matchAll) {
                // Rank in-stock parts higher
                if (p.getCurrentStock() > 0) {
                    score += 100;
                }
                
                // Add sales count score
                int sales = salesCount.getOrDefault(p.getId(), 0);
                score += sales * 10;
                
                // Add recency score (last updated)
                if (p.getUpdatedAt() != null) {
                    long ageDays = java.time.temporal.ChronoUnit.DAYS.between(p.getUpdatedAt(), java.time.LocalDateTime.now());
                    if (ageDays < 7) score += 20;
                    else if (ageDays < 30) score += 10;
                }

                results.add(new SearchResult(p, score));
            }
        }

        // Sort by score descending
        results.sort(Comparator.comparingInt(SearchResult::getScore).reversed());

        return results.stream().map(SearchResult::getPart).collect(Collectors.toList());
    }

    /**
     * Smart autocomplete suggestions based on previously entered searches and names.
     */
    public String getAutocompleteSuggestion(String input) {
        if (input == null || input.isBlank()) return null;
        String normInput = normalizeArabic(input);

        // Call findAll() ONCE and filter in-memory (avoids double DB query)
        List<Part> allActiveParts = partDao.findAll().stream()
                .filter(Part::isActive)
                .collect(Collectors.toList());

        // First try: parts whose name STARTS WITH the input
        List<Part> matched = allActiveParts.stream()
                .filter(p -> normalizeArabic(p.getFullName()).startsWith(normInput))
                .collect(Collectors.toList());

        if (matched.isEmpty()) {
            // Fallback: parts whose name CONTAINS the input
            matched = allActiveParts.stream()
                    .filter(p -> normalizeArabic(p.getFullName()).contains(normInput))
                    .collect(Collectors.toList());
        }

        if (matched.isEmpty()) return null;

        // Rank parts by sales count
        Map<Integer, Integer> sales = getSalesCountMap();
        matched.sort((p1, p2) -> Integer.compare(
                sales.getOrDefault(p2.getId(), 0),
                sales.getOrDefault(p1.getId(), 0)
        ));

        // Find the best match
        Part best = matched.get(0);
        String name = best.getFullName();

        // Find index of match
        String normName = normalizeArabic(name);
        int idx = normName.indexOf(normInput);
        if (idx == 0) {
            // Returns the exact casing from database starting with the match
            return name;
        }

        return null;
    }

    private Map<Integer, Integer> getSalesCountMap() {
        Map<Integer, Integer> map = new HashMap<>();
        String sql = "SELECT part_id, SUM(quantity) FROM invoice_items GROUP BY part_id";
        try (Connection c = com.kiastore.db.ConnectionFactory.borrow();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt(1), rs.getInt(2));
            }
        } catch (SQLException ignore) {}
        return map;
    }

    private static class SearchResult {
        private final Part part;
        private final int score;

        public SearchResult(Part part, int score) {
            this.part = part;
            this.score = score;
        }

        public Part getPart() { return part; }
        public int getScore() { return score; }
    }

    /**
     * Looks up a part by a barcode scan string.
     * Strips spaces and special characters, then does an exact match on
     * barcode or part_number_normalized.
     */
    public Part findByBarcode(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) return null;
        String normalized = rawInput.trim().replaceAll("[^A-Za-z0-9\\u0600-\\u06FF]", "").toUpperCase();
        if (normalized.isEmpty()) return null;
        return partDao.findByBarcodeOrNormalizedNumber(normalized);
    }
}
