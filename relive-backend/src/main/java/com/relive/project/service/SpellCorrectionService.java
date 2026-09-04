package com.relive.project.service;

import com.relive.project.entity.Domain;
import com.relive.project.repository.FacePersonRepository;
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
    private final FacePersonRepository facePersonRepository;

    private final SymSpellUtil symSpell = new SymSpellUtil();

    /** Rebuild the dictionary from this user's own data — not a generic
     *  English dictionary — so corrections stay relevant to what's actually
     *  searchable. Called after each import batch finishes. */
    public synchronized void rebuildDictionary() {
        List<String> words = new ArrayList<>();
        words.addAll(mediaKeywordRepository.findDistinctKeywordsByDomain(Domain.VOCAB));
        words.addAll(mediaKeywordRepository.findDistinctKeywordsByDomain(Domain.LOCATION));
        facePersonRepository.findAll().forEach(p -> {
            if (p.getName() != null && !p.getName().isBlank()) words.add(p.getName());
        });
        symSpell.rebuild(words);
        System.out.println("Spelling dictionary rebuilt: " + words.size() + " words");
    }

    public String correctQuery(String query) {
        return symSpell.correctQuery(query);
    }
}