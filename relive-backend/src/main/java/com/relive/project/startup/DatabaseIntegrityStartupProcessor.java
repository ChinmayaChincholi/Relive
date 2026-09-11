package com.relive.project.startup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarantees media_keywords / locations / face_embeddings (and any
 * face_persons left with zero embeddings) can never outlive the media row
 * they belong to, regardless of how that media row was deleted.
 *
 * MediaService.deleteMedia() already does this for app-initiated deletes;
 * this covers every other path via real DB-level triggers.
 */
@Component
public class DatabaseIntegrityStartupProcessor {

    @PersistenceContext
    private EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void enforceCascadeDeletes() {

        entityManager.createNativeQuery(
                "CREATE TRIGGER IF NOT EXISTS trg_media_delete_keywords " +
                        "AFTER DELETE ON media " +
                        "BEGIN DELETE FROM media_keywords WHERE media_id = OLD.id; END;"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "CREATE TRIGGER IF NOT EXISTS trg_media_delete_locations " +
                        "AFTER DELETE ON media " +
                        "BEGIN DELETE FROM locations WHERE media_id = OLD.id; END;"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "CREATE TRIGGER IF NOT EXISTS trg_media_delete_face_embeddings " +
                        "AFTER DELETE ON media " +
                        "BEGIN DELETE FROM face_embeddings WHERE media_id = OLD.id; END;"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "CREATE TRIGGER IF NOT EXISTS trg_face_embedding_delete_orphan_person " +
                        "AFTER DELETE ON face_embeddings " +
                        "WHEN OLD.person_id IS NOT NULL " +
                        "BEGIN DELETE FROM face_persons WHERE id = OLD.person_id " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM face_embeddings WHERE person_id = OLD.person_id" +
                        "); END;"
        ).executeUpdate();

        // One-time (idempotent) cleanup of existing orphan records.

        entityManager.createNativeQuery(
                "DELETE FROM media_keywords " +
                        "WHERE media_id NOT IN (SELECT id FROM media)"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM locations " +
                        "WHERE media_id NOT IN (SELECT id FROM media)"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM face_embeddings " +
                        "WHERE media_id NOT IN (SELECT id FROM media)"
        ).executeUpdate();

        entityManager.createNativeQuery(
                "DELETE FROM face_persons " +
                        "WHERE id NOT IN (" +
                        "SELECT DISTINCT person_id FROM face_embeddings " +
                        "WHERE person_id IS NOT NULL)"
        ).executeUpdate();
    }
}