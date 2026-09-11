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
            // media_keywords now only ever holds VOCAB rows (LOCATION moved
            // to its own table below), so no domain filter is needed here
            // anymore.
            words.addAll(mediaKeywordRepository.findDistinctKeywords());
            words.addAll(locationRepository.findDistinctLocationNames());
            facePersonRepository.findAll().forEach(p -> {
                if (p.getName() != null && !p.getName().isBlank())
                    words.add(p.getName());
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