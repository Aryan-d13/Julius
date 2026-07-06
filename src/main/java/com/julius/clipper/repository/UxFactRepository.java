package com.julius.clipper.repository;

import com.julius.clipper.domain.UxFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UxFactRepository extends JpaRepository<UxFact, String> {
    List<UxFact> findBySlotAndLanguageAndAudienceScopeAndEnabled(
            String slot, 
            String language, 
            String audienceScope, 
            boolean enabled
    );
}
