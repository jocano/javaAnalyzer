package com.example.sca.repository;

import com.example.sca.model.ComponentType;
import com.example.sca.model.JavaComponent;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Couchbase repository for {@link JavaComponent} documents.
 *
 * <p>All N1QL queries filter by {@code projectRoot} so that multiple analyzed
 * projects can coexist in the same bucket without conflict.
 */
@Repository
public interface JavaComponentRepository
        extends CouchbaseRepository<JavaComponent, String> {

    /** All components belonging to a project. */
    List<JavaComponent> findByProjectRoot(String projectRoot);

    /** Components filtered by stereotype. */
    List<JavaComponent> findByProjectRootAndComponentType(
        String projectRoot, ComponentType componentType);

    /** Find by exact simple name (case-sensitive). */
    List<JavaComponent> findByProjectRootAndSimpleName(
        String projectRoot, String simpleName);

    /** Find by simple name, case-insensitive (LIKE pattern). */
    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND projectRoot = $1 AND LOWER(simpleName) LIKE LOWER($2)")
    List<JavaComponent> findByProjectRootAndSimpleNameContaining(
        String projectRoot, String pattern);

    /** Delete all documents for a given project. */
    void deleteByProjectRoot(String projectRoot);
}
