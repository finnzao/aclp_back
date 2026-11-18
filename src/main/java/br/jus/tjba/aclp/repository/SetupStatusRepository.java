package br.jus.tjba.aclp.repository;

import br.jus.tjba.aclp.model.SetupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SetupStatusRepository extends JpaRepository<SetupStatus, String> {

    @Query("SELECT s.completed FROM SetupStatus s WHERE s.id = 'SINGLE_ROW'")
    Optional<Boolean> isSetupCompleted();

    default SetupStatus getSetupStatus() {
        return findById("SINGLE_ROW").orElse(
                SetupStatus.builder()
                        .id("SINGLE_ROW")
                        .completed(false)
                        .build()
        );
    }

    default boolean isCompleted() {
        return isSetupCompleted().orElse(false);
    }

    default void markAsCompleted(String adminEmail, String clientIp) {
        SetupStatus status = getSetupStatus();
        status.markAsCompleted(adminEmail, clientIp);
        save(status);
    }

    default void resetSetup() {
        deleteAll();
    }
}