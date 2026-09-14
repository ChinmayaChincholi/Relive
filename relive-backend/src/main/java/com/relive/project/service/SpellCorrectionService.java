package com.relive.project.service;

import com.relive.project.repository.FacePersonRepository;
import com.relive.project.repository.LocationRepository;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.util.SymSpellUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpellCorrectionService {
    private final MediaKeywordRepository mediaKeywordRepository;
    private final LocationRepository locationRepository;
    private final FacePersonRepository facePersonRepository;
    private final SymSpellUtil symSpell = new SymSpellUtil();
    private final java.util.concurrent.locks.ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    /**
     * Rebuild the dictionary from this user's own data — not a generic
     * English dictionary — so corrections stay relevant to what's actually
     * searchable. Called after each import batch finishes.
     */
    public void rebuildDictionary() {
        lock.writeLock().lock();
        try {
            List<String> words = new ArrayList<>();
            words.addAll(mediaKeywordRepository.findDistinctKeywords());

            // Location names (and person names) can be multi-word phrases
            // ("tamil nadu", "los angeles"). correctQuery() corrects each
            // whitespace-separated token of the user's query independently,
            // so a multi-word phrase added to the dictionary as one whole
            // string is invisible to a single-word query token --- "nadu"
            // can never edit-distance-match "tamil nadu" (a 6-character
            // length gap, far past MAX_EDIT_DISTANCE). Confirmed bug: "tamil
            // nadu" typed correctly as two words was corrected to "tamil
            // navy" because "nadu" alone was never in the dictionary at any
            // granularity, while "navy" (an unrelated VOCAB color keyword)
            // coincidentally sat exactly 2 edits away. Adding each
            // constituent word alongside the full phrase means "nadu" is
            // now an exact dictionary entry and short-circuits straight
            // past the edit-distance search.
            for (String locationName : locationRepository.findDistinctLocationNames()) {
                words.add(locationName);
                for (String part : locationName.split("\\s+")) {
                    if (!part.isBlank()) words.add(part);
                }
            }

            facePersonRepository.findAll().forEach(p -> {
                if (p.getName() != null && !p.getName().isBlank()) {
                    words.add(p.getName());
                    for (String part : p.getName().split("\\s+")) {
                        if (!part.isBlank()) words.add(part);
                    }
                }
            });

            symSpell.rebuild(words);
            System.out.println("Spelling dictionary rebuilt: " + words.size() + " words");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public String correctQuery(String query) {
        lock.readLock().lock();
        try {
            String result = symSpell.correctQuery(query);
            System.out.println("[SpellCorrection] raw=\"" + query + "\" -> corrected=\"" + result + "\"");
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}