package com.julius.clipper.repository;

import com.julius.clipper.domain.SubtitleStyle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubtitleStyleRepository extends JpaRepository<SubtitleStyle, String> {
}
