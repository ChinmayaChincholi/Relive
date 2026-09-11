package com.relive.project.service;

import com.relive.project.client.FaceClient;
import com.relive.project.dto.FacePersonDTO;
import com.relive.project.entity.*;
import com.relive.project.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FaceService {

    private final FaceClient faceClient;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FacePersonRepository facePersonRepository;
    private final MediaRepository mediaRepository;

    @Value("${relive.data.dir}")
    private String dataDir;


    /** Extracts faces and assigns each to a person via nearest-neighbor
     *  matching — no longer triggers clustering itself; MediaUploadService
     *  triggers clusterUnnamedPool() once per batch instead of once per
     *  image. */
    public void extractAndAssignFaces(Long mediaId, String absoluteImagePath) {
        try {
            long extractStart = System.nanoTime();
            Map<String, Object> response = faceClient.extractFaces(absoluteImagePath, mediaId);
            List<Map<String, Object>> faces = (List<Map<String, Object>>) response.get("faces");
            double extractElapsed = (System.nanoTime() - extractStart) / 1_000_000_000.0;
            System.out.printf("[FaceService] media_id=%d step=extractFaces (AI service call) face_count=%d took=%.3fs%n",
                    mediaId, faces == null ? 0 : faces.size(), extractElapsed);

            if (faces == null || faces.isEmpty()) return;
            Media media = mediaRepository.findById(mediaId).orElseThrow();

            long assignStart = System.nanoTime();
            for (Map<String, Object> face : faces) {
                String cropPathAbsolute = (String) face.get("crop_path");
                List<Double> embedding = (List<Double>) face.get("embedding");
                Double confidence = face.get("confidence") != null
                        ? ((Number) face.get("confidence")).doubleValue()
                        : 1.0;
                if (embedding == null || embedding.isEmpty()) continue;
                String embeddingCsv = embedding.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                String cropPath = relativizeToDataDir(cropPathAbsolute);
                FaceEmbedding fe = FaceEmbedding.builder()
                        .cropPath(cropPath)
                        .embeddingCsv(embeddingCsv)
                        .confidence(confidence)
                        .media(media)
                        .build();
                assignOrCreatePerson(fe);
            }
            double assignElapsed = (System.nanoTime() - assignStart) / 1_000_000_000.0;
            System.out.printf("[FaceService] media_id=%d step=assignOrCreatePerson (%d faces) took=%.3fs%n",
                    mediaId, faces.size(), assignElapsed);
        } catch (Exception e) {
            System.out.println("Face extraction failed for media " + mediaId + ": " + e.getMessage());
        }
    }

    /** The AI service returns an absolute filesystem path for each face
     *  crop. FaceController's /crop endpoint deliberately expects a path
     *  RELATIVE to dataDir (Paths.get(dataDir, path)) — this is a security
     *  boundary, not an oversight: accepting an arbitrary absolute path
     *  from the client would make that endpoint able to read any file on
     *  disk. Store the relative form so the join in that controller
     *  actually resolves to the real file. */
    private String relativizeToDataDir(String absolutePath) {
        try {
            return Paths.get(dataDir)
                    .relativize(Paths.get(absolutePath))
                    .toString();
        } catch (Exception e) {
            System.out.println("Could not relativize crop path: " + absolutePath + " — " + e.getMessage());
            return absolutePath; // fallback — will still 404, but won't crash extraction
        }
    }


    /** Renamed from clusterAndAssign. Only embeddings belonging to an
     *  UNNAMED person are sent for re-clustering — named identities are
     *  never included, so a periodic re-cluster can never silently
     *  reshuffle, split, or merge an identity the user already confirmed. */
    @Transactional
    public void clusterUnnamedPool() {
        try {
            List<FaceEmbedding> unnamedPool = faceEmbeddingRepository.findAll().stream()
                    .filter(fe -> fe.getPerson() == null || isUnnamed(fe.getPerson()))
                    .collect(Collectors.toList());

            if (unnamedPool.isEmpty()) return;

            List<List<Double>> embeddingVectors = unnamedPool.stream()
                    .map(fe -> {
                        String[] parts = fe.getEmbeddingCsv().split(",");
                        List<Double> vec = new ArrayList<>();
                        for (String p : parts) vec.add(Double.parseDouble(p));
                        return vec;
                    })
                    .collect(Collectors.toList());

            List<Integer> labels = faceClient.clusterFaces(embeddingVectors);

            if (labels == null || labels.size() != unnamedPool.size()) {
                System.out.println("Clustering returned unexpected label count. Skipping assignment.");
                return;
            }

            Map<Integer, List<FaceEmbedding>> clusters = new HashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                int label = labels.get(i);
                if (label == -1) continue; // HDBSCAN noise — leave ungrouped for now
                clusters.computeIfAbsent(label, k -> new ArrayList<>()).add(unnamedPool.get(i));
            }

            Map<Integer, FacePerson> clusterToExistingPerson = new HashMap<>();
            for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {
                for (FaceEmbedding fe : entry.getValue()) {
                    if (fe.getPerson() != null) {
                        clusterToExistingPerson.put(entry.getKey(), fe.getPerson());
                        break;
                    }
                }
            }

            for (Map.Entry<Integer, List<FaceEmbedding>> entry : clusters.entrySet()) {
                int clusterLabel = entry.getKey();
                List<FaceEmbedding> clusterEmbeddings = entry.getValue();

                FacePerson person = clusterToExistingPerson.get(clusterLabel);
                if (person == null) {
                    person = FacePerson.builder().name(null).build();
                    facePersonRepository.save(person);
                }

                for (FaceEmbedding fe : clusterEmbeddings) {
                    fe.setPerson(person);
                    faceEmbeddingRepository.save(fe);
                }
            }

            List<FacePerson> allPersons = facePersonRepository.findAll();
            for (FacePerson person : allPersons) {
                if (isUnnamed(person) && faceEmbeddingRepository.findByPerson(person).isEmpty()) {
                    facePersonRepository.delete(person);
                }
            }

            System.out.println("Unnamed-pool clustering complete. Clusters: " + clusters.size());

        } catch (Exception e) {
            System.out.println("Face clustering failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isUnnamed(FacePerson person) {
        return person.getName() == null || person.getName().isBlank();
    }

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

        result.sort((a, b) -> Integer.compare(b.getMediaIds().size(), a.getMediaIds().size()));

        return result;
    }

    public void namePerson(Long personId, String name) {
        FacePerson person = facePersonRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        person.setName(name);
        facePersonRepository.save(person);
    }

    @Transactional
    public void mergePeople(Long personId1, Long personId2, String overrideName) {
        FacePerson person1 = facePersonRepository.findById(personId1)
                .orElseThrow(() -> new RuntimeException("Person " + personId1 + " not found"));
        FacePerson person2 = facePersonRepository.findById(personId2)
                .orElseThrow(() -> new RuntimeException("Person " + personId2 + " not found"));

        String finalName = overrideName != null && !overrideName.isBlank()
                ? overrideName
                : (person1.getName() != null ? person1.getName() : person2.getName());

        person1.setName(finalName);
        facePersonRepository.save(person1);

        List<FaceEmbedding> embeddings2 = faceEmbeddingRepository.findByPerson(person2);
        for (FaceEmbedding fe : embeddings2) {
            fe.setPerson(person1);
            faceEmbeddingRepository.save(fe);
        }

        facePersonRepository.delete(person2);

        System.out.println("Merged person " + personId2 + " into " + personId1 + " as '" + finalName + "'");
    }

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

    public List<String> getAllPersonNames() {
        return facePersonRepository.findAll().stream()
                .map(FacePerson::getName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toList());
    }

    public List<Long> getMediaIdsForPersonExact(String resolvedName) {
        List<FacePerson> persons = facePersonRepository.findByNameIgnoreCase(resolvedName);
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

    private String pickBestRepresentativeCrop(List<FaceEmbedding> embeddings) {
        return embeddings.stream()
                .filter(fe -> fe.getConfidence() != null)
                .max(Comparator.comparingDouble(FaceEmbedding::getConfidence))
                .map(FaceEmbedding::getCropPath)
                .orElse(embeddings.get(0).getCropPath());
    }

    private static final double PERSON_MATCH_THRESHOLD = 0.5;

    private void assignOrCreatePerson(FaceEmbedding newEmbedding) {
        double[] newVec = parseEmbedding(newEmbedding.getEmbeddingCsv());

        List<FaceEmbedding> assigned = faceEmbeddingRepository.findAll().stream()
                .filter(fe -> fe.getPerson() != null)
                .collect(Collectors.toList());

        FacePerson bestMatch = null;
        double bestScore = PERSON_MATCH_THRESHOLD;

        for (FaceEmbedding candidate : assigned) {
            double[] candidateVec = parseEmbedding(candidate.getEmbeddingCsv());
            double similarity = cosineSimilarity(newVec, candidateVec);
            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = candidate.getPerson();
            }
        }

        if (bestMatch != null) {
            newEmbedding.setPerson(bestMatch);
        } else {
            FacePerson newPerson = FacePerson.builder().name(null).build();
            facePersonRepository.save(newPerson);
            newEmbedding.setPerson(newPerson);
        }

        faceEmbeddingRepository.save(newEmbedding);
    }

    private double[] parseEmbedding(String csv) {
        String[] parts = csv.split(",");
        double[] vec = new double[parts.length];
        for (int i = 0; i < parts.length; i++) vec[i] = Double.parseDouble(parts[i]);
        return vec;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}