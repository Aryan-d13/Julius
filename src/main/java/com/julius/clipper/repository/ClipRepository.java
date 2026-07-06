package com.julius.clipper.repository;

import com.julius.clipper.domain.Clip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClipRepository extends JpaRepository<Clip, String> {
}
