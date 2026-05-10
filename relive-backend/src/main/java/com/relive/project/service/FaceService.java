package com.relive.project.service;

import com.relive.project.client.FaceClient;
import com.relive.project.dto.FacePersonDTO;
import com.relive.project.entity.*;
import com.relive.project.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceClient faceClient;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FacePersonRepository facePersonRepository;
    private final MediaRepository mediaRepository;

    // ── Face extraction (called after each image is processed) ────────

    public void extractAndStoreFaces(Long mediaId, String absoluteImagePath) {
        try {
            Map<String, Object> response = faceClient.extractFaces(absoluteImagePath, mediaId);
            List<Map<String, Object>> faces = (List<Map<String, Object>>) response.get("faces");

            if (faces == null || faces.isEmpty()) return;

            Media media = mediaRepository.findById(mediaId).orElseThrow();

            for (Map<String, Object> face : faces) {
                String cropPath       = (String) face.get("crop_path");
                List<Double> embedding = (List<Double>) face.get("embedding");
                Double confidence = face.get("confidence") != null
                        ? ((Number) face.get("confidence")).doubleValue()
                        : 1.0;

                if (embedding == null || embedding.isEmpty()) continue;

                String embeddingCsv = embedding.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

                FaceEmbedding fe = FaceEmbedding.builder()
                        .cropPath(cropPath)
                        .embeddingCsv(embeddingCsv)
                        .confidence(confidence)
                        .media(media)
                        .person(null) // unassigned until clustering
                        .build();

                faceEmbeddingRepository.save(fe);
            }
        } catch (Exception e) {
            System.out.println("Face extraction failed for media " + mediaId + ": " + e.getMessage());
        }
    }

    // ── Clustering ────────────────────────────────────────────────────

    /**
     * Smart incremental clustering.
     *  - Only triggers when there are new unassigned embeddings.
     *  - Clusters ALL embeddings together so new faces are compared against existing ones.
     *  - Preserves names on existing FacePerson records.
     *
     * NOTE: Called by MediaProcessingService after each image finishes processing,
     * NOT from the read path (getPeople). This keeps the Faces page fast.
     */
    @Transactional
    public void clusterAndAssign() {
        try {
            List<FaceEmbedding> unassigned = faceEmbeddingRepository.findByPersonIsNull();
            if (unassigned.isEmpty()) return; // nothing new — preserve existing state

            List<FaceEmbedding> allEmbeddings = faceEmbeddingRepository.findAll();
            if (allEmbeddings.isEmpty()) return;

            // Build embedding matrix for the clustering call
            List<List<Double>> embeddingVectors = allEmbeddings.stream()
                    .map(fe -> {
                        String[] parts = fe.getEmbeddingCsv().split(",");
                        List<Double> vec = new ArrayList<>();
                        for (String p : parts) vec.add(Double.parseDouble(p));
                        return vec;
                    })
                    .collect(Collectors.toList());

            List<Integer> labels = faceClient.clusterFaces(embeddingVectors);

            if (labels == null || labels.size() != allEmbeddings.size()) {
                System.out.println("Clustering returned unexpected label count. Skipping assignment.");
                return;
            }

            // Group embeddings by cluster label (-1 = noise, skip)
            Map<Integer, List<FaceEmbedding>> clusters = new HashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                int label = labels.get(i);
                if (label == -1) continue;
                clusters.computeIfAbsent(label, k -> new ArrayList<>()).add(allEmbeddings.get(i));
            }

            // Match each cluster to an existing FacePerson if any embedding in the
            // cluster is already assigned (preserves the existing name).
            Map<Integer, FacePerson> clusterToExistingPerson = new HashMap<>();
            for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {
                for (FaceEmbedding fe : entry.getValue()) {
                    if (fe.getPerson() != null) {
                        clusterToExistingPerson.put(entry.getKey(), fe.getPerson());
                        break;
                    }
                }
            }

            // Assign embeddings to persons, creating new unnamed persons for new clusters
            for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {
                int clusterLabel = entry.getKey();
                List<FaceEmbedding> clusterEmbeddings = entry.getValue();

                FacePerson person;
                if (clusterToExistingPerson.containsKey(clusterLabel)) {
                    person = clusterToExistingPerson.get(clusterLabel);
                } else {
                    person = FacePerson.builder().name(null).build();
                    facePersonRepository.save(person);
                }

                for (FaceEmbedding fe : clusterEmbeddings) {
                    fe.setPerson(person);
                    faceEmbeddingRepository.save(fe);
                }
            }

            // Remove any FacePerson records left with no embeddings after reclustering
            List<FacePerson> allPersons = facePersonRepository.findAll();
            for (FacePerson person : allPersons) {
                if (faceEmbeddingRepository.findByPerson(person).isEmpty()) {
                    facePersonRepository.delete(person);
                }
            }

            System.out.println("Clustering complete. Clusters: " + clusters.size());

        } catch (Exception e) {
            System.out.println("Face clustering failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Queries ───────────────────────────────────────────────────────

    /**
     * Returns the list of known persons sorted by number of photos (descending).
     * Does NOT trigger clustering — clustering happens in the background after each image is processed.
     */
    public List<FacePersonDTO> getPeople() {
        List<FacePerson> persons = facePersonRepository.findAll();
        List<FacePersonDTO> result = new ArrayList<>();

        for (FacePerson person : persons) {
            List<FaceEmbedding> embeddings = faceEmbeddingRepository.findByPerson(person);
            if (embeddings.isEmpty()) continue;

            List<String> cropPaths = embeddings.stream()
                    .filter(fe -> fe.getCropPath() != null)
                    .map(FaceEmbedding::getCropPath)
                    .collect(Collectors.toList());

            List<Long> mediaIds = embeddings.stream()
                    .filter(fe -> fe.getMedia() != null)
                    .map(fe -> fe.getMedia().getId())
                    .distinct()
                    .collect(Collectors.toList());

            String representativeCrop = pickBestRepresentativeCrop(embeddings);

            FacePersonDTO dto = new FacePersonDTO();
            dto.setPersonId(person.getId());
            dto.setName(person.getName());
            dto.setRepresentativeCrop(representativeCrop);
            dto.setCropPaths(cropPaths);
            dto.setMediaIds(mediaIds);
            result.add(dto);
        }

        // Sort by photo count descending
        result.sort((a, b) -> Integer.compare(b.getMediaIds().size(), a.getMediaIds().size()));

        return result;
    }

    public void namePerson(Long personId, String name) {
        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        person.setName(name);
        facePersonRepository.save(person);
    }

    /**
     * Merge two face groups into one. All embeddings from person2 are reassigned to person1.
     * Person1 gets the provided name (or falls back to existing names).
     * Person2 is deleted.
     */
    @Transactional
    public void mergePeople(Long personId1, Long personId2, String overrideName) {
        FacePerson person1 = facePersonRepository.findById(personId1)
                .orElseThrow(() -> new RuntimeException("Person " + personId1 + " not found"));
        FacePerson person2 = facePersonRepository.findById(personId2)
                .orElseThrow(() -> new RuntimeException("Person " + personId2 + " not found"));

        // Determine final name
        String finalName = overrideName != null && !overrideName.isBlank()
                ? overrideName
                : (person1.getName() != null ? person1.getName() : person2.getName());

        person1.setName(finalName);
        facePersonRepository.save(person1);

        // Reassign all embeddings from person2 to person1
        List<FaceEmbedding> embeddings2 = faceEmbeddingRepository.findByPerson(person2);
        for (FaceEmbedding fe : embeddings2) {
            fe.setPerson(person1);
            faceEmbeddingRepository.save(fe);
        }

        // Delete person2
        facePersonRepository.delete(person2);

        System.out.println("Merged person " + personId2 + " into " + personId1 + " as '" + finalName + "'");
    }

    /**
     * Delete a face person group and all its embeddings.
     */
    @Transactional
    public void deletePerson(Long personId) {
        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        List<FaceEmbedding> embeddings = faceEmbeddingRepository.findByPerson(person);
        for (FaceEmbedding fe : embeddings) {
            fe.setPerson(null);
            faceEmbeddingRepository.save(fe);
        }
        faceEmbeddingRepository.deleteAll(embeddings);
        facePersonRepository.delete(person);
    }

    public List<Long> getMediaIdsForPersonName(String name) {
        // Use case-insensitive partial match so that:
        //   "chinmaya" matches stored name "Chinmaya"
        //   "chin"     matches stored name "Chinmaya" (partial)
        List<FacePerson> persons = facePersonRepository.findByNameContainingIgnoreCase(name);
        if (persons.isEmpty()) return Collections.emptyList();

        return persons.stream()
                .flatMap(person -> faceEmbeddingRepository.findByPerson(person).stream())
                .filter(fe -> fe.getMedia() != null)
                .map(fe -> fe.getMedia().getId())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Media> getPhotosForPerson(Long personId) {
        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));

        return faceEmbeddingRepository.findByPerson(person).stream()
                .filter(fe -> fe.getMedia() != null)
                .map(FaceEmbedding::getMedia)
                .distinct()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String pickBestRepresentativeCrop(List<FaceEmbedding> embeddings) {
        return embeddings.stream()
                .filter(fe -> fe.getConfidence() != null)
                .max(Comparator.comparingDouble(FaceEmbedding::getConfidence))
                .map(FaceEmbedding::getCropPath)
                .orElse(embeddings.get(0).getCropPath());
    }
}