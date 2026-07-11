package com.julius.clipper.repository;

import com.julius.clipper.domain.RenderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RenderProfileRepository extends JpaRepository<RenderProfile, String> {
}
