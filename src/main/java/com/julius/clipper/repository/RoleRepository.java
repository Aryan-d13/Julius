package com.julius.clipper.repository;

import com.julius.clipper.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(String name);

    @Query(value = "SELECT permission FROM role_permissions WHERE role_id = :roleId", nativeQuery = true)
    List<String> findPermissionsByRoleId(@Param("roleId") String roleId);
}
