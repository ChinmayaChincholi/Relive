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
    private final UserRepository userRepository;

    public void extractAndStoreFaces(Long mediaId, String absoluteImagePath) {

        try {

            Map<String, Object> response =
                    faceClient.extractFaces(absoluteImagePath, mediaId);

            List<Map<String, Object>> faces =
                    (List<Map<String, Object>>) response.get("faces");

            if (faces == null || faces.isEmpty()) return;

            Media media = mediaRepository.findById(mediaId).orElseThrow();

            for (Map<String, Object> face : faces) {

                String cropPath = (String) face.get("crop_path");
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
            System.out.println("Face extraction failed for media "
                    + mediaId + ": " + e.getMessage());
        }
    }

    /**
     * Smart clustering — only runs when there are unassigned embeddings.
     * Preserves all existing named persons.
     * Assigns unassigned faces to existing persons or creates new ones.
     */
    @Transactional
    public void clusterAndAssign(String email) {

        User user = userRepository.findByEmail(email).orElseThrow();

        // Only get UNASSIGNED embeddings — don't touch already-assigned ones
        List<FaceEmbedding> unassigned =
                faceEmbeddingRepository.findByPersonIsNullAndMedia_User_Email(email);

        if (unassigned.isEmpty()) {
            // Nothing new to cluster — return without touching existing persons
            return;
        }

        // Get ALL embeddings to recluster everything together
        // (new faces need to be compared against all existing faces)
        List<FaceEmbedding> allEmbeddings =
                faceEmbeddingRepository.findByMedia_User_Email(email);

        if (allEmbeddings.isEmpty()) return;

        // Build embedding vectors for clustering
        List<List<Double>> embeddingVectors = allEmbeddings.stream()
                .map(fe -> {
                    String[] parts = fe.getEmbeddingCsv().split(",");
                    List<Double> vec = new ArrayList<>();
                    for (String p : parts) vec.add(Double.parseDouble(p));
                    return vec;
                })
                .collect(Collectors.toList());

        // Run clustering on ALL embeddings
        List<Integer> labels = faceClient.clusterFaces(embeddingVectors);

        // Group embeddings by cluster label
        Map<Integer, List<FaceEmbedding>> clusters = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            int label = labels.get(i);
            if (label == -1) continue; // noise
            clusters.computeIfAbsent(label, k -> new ArrayList<>())
                    .add(allEmbeddings.get(i));
        }

        // Get existing persons for this user (these have names we must preserve)
        List<FacePerson> existingPersons =
                facePersonRepository.findByUser_Email(email);

        // Build a map of existing person → their embedding IDs
        // so we can match new clusters to existing persons
        Map<Long, Set<Long>> personToEmbeddingIds = new HashMap<>();
        for (FacePerson person : existingPersons) {
            List<FaceEmbedding> personEmbeddings =
                    faceEmbeddingRepository.findByPerson(person);
            Set<Long> ids = personEmbeddings.stream()
                    .map(FaceEmbedding::getId)
                    .collect(Collectors.toSet());
            personToEmbeddingIds.put(person.getId(), ids);
        }

        // Match each new cluster to an existing person or create a new one
        // Matching: if a cluster contains any embedding already assigned
        // to an existing person, it belongs to that person
        Map<Integer, FacePerson> clusterToExistingPerson = new HashMap<>();

        for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {

            int clusterLabel = entry.getKey();
            List<FaceEmbedding> clusterEmbeddings = entry.getValue();

            // Check if any embedding in this cluster is already assigned
            for (FaceEmbedding fe : clusterEmbeddings) {
                if (fe.getPerson() != null) {
                    clusterToExistingPerson.put(clusterLabel, fe.getPerson());
                    break;
                }
            }
        }

        // Now assign all embeddings to persons
        for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {

            int clusterLabel = entry.getKey();
            List<FaceEmbedding> clusterEmbeddings = entry.getValue();

            FacePerson person;

            if (clusterToExistingPerson.containsKey(clusterLabel)) {
                // Use existing person — preserves their name
                person = clusterToExistingPerson.get(clusterLabel);
            } else {
                // New cluster — create a new unnamed person
                person = FacePerson.builder()
                        .name(null)
                        .user(user)
                        .build();
                facePersonRepository.save(person);
            }

            // Assign all embeddings in this cluster to this person
            for (FaceEmbedding fe : clusterEmbeddings) {
                fe.setPerson(person);
                faceEmbeddingRepository.save(fe);
            }
        }

        // Clean up any persons that ended up with no embeddings
        // (can happen if clustering changed significantly)
        for (FacePerson person : existingPersons) {
            List<FaceEmbedding> remaining =
                    faceEmbeddingRepository.findByPerson(person);
            if (remaining.isEmpty()) {
                facePersonRepository.delete(person);
            }
        }
    }

    public List<FacePersonDTO> getPeopleForUser(String email) {

        // Only cluster if there are new unassigned embeddings
        clusterAndAssign(email);

        List<FacePerson> persons = facePersonRepository.findByUser_Email(email);
        List<FacePersonDTO> result = new ArrayList<>();

        for (FacePerson person : persons) {

            List<FaceEmbedding> embeddings =
                    faceEmbeddingRepository.findByPerson(person);

            if (embeddings.isEmpty()) continue;

            List<String> cropPaths = embeddings.stream()
                    .map(FaceEmbedding::getCropPath)
                    .collect(Collectors.toList());

            List<Long> mediaIds = embeddings.stream()
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

        return result;
    }

    private String pickBestRepresentativeCrop(List<FaceEmbedding> embeddings) {

        return embeddings.stream()
                .filter(fe -> fe.getConfidence() != null)
                .max(Comparator.comparingDouble(FaceEmbedding::getConfidence))
                .map(FaceEmbedding::getCropPath)
                .orElse(embeddings.get(0).getCropPath());
    }

    public void namePerson(Long personId, String name, String email) {

        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));

        if (!person.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }

        person.setName(name);
        facePersonRepository.save(person);
    }

    public List<Long> getMediaIdsForPersonName(String name, String email) {

        List<FacePerson> persons =
                facePersonRepository.findByNameAndUser_Email(name, email);

        if (persons.isEmpty()) return Collections.emptyList();

        // Collect media IDs from ALL persons with this name
        // Handles case where same person is named identically across multiple clusters
        return persons.stream()
                .flatMap(person ->
                        faceEmbeddingRepository.findByPerson(person).stream()
                )
                .map(fe -> fe.getMedia().getId())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Media> getPhotosForPerson(Long personId, String email) {

        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));

        if (!person.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized");
        }

        return faceEmbeddingRepository.findByPerson(person)
                .stream()
                .map(FaceEmbedding::getMedia)
                .distinct()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .collect(Collectors.toList());
    }
}