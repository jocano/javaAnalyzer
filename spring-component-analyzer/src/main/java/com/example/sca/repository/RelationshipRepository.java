package com.example.sca.repository;

import com.example.sca.model.ComponentRelationship;
import com.example.sca.model.ComponentType;
import org.springframework.data.couchbase.repository.CouchbaseRepository;
import org.springframework.data.couchbase.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data Couchbase repository for {@link ComponentRelationship} documents.
 */
@Repository
public interface RelationshipRepository
        extends CouchbaseRepository<ComponentRelationship, String> {

    /** All relationships belonging to a project. */
    List<ComponentRelationship> findByProjectRoot(String projectRoot);

    /** All edges that originate from a given qualified name. */
    List<ComponentRelationship> findByProjectRootAndFromQualifiedName(
        String projectRoot, String fromQualifiedName);

    /** All edges that point to a given simple name. */
    List<ComponentRelationship> findByProjectRootAndToSimpleName(
        String projectRoot, String toSimpleName);

    /** All edges from components of a certain stereotype. */
    List<ComponentRelationship> findByProjectRootAndFromType(
        String projectRoot, ComponentType fromType);

    /** All edges pointing to components of a certain stereotype. */
    List<ComponentRelationship> findByProjectRootAndToType(
        String projectRoot, ComponentType toType);

    /**
     * Controller → Service wiring: edges where the source is a CONTROLLER
     * and the target is a SERVICE.
     */
    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND projectRoot = $1 "
         + "AND fromType = 'CONTROLLER' AND toType = 'SERVICE'")
    List<ComponentRelationship> findControllerToServiceEdges(String projectRoot);

    /**
     * Service → Repository wiring.
     */
    @Query("#{#n1ql.selectEntity} WHERE #{#n1ql.filter} AND projectRoot = $1 "
         + "AND fromType = 'SERVICE' AND toType = 'REPOSITORY'")
    List<ComponentRelationship> findServiceToRepositoryEdges(String projectRoot);

    /** Delete all documents for a given project. */
    void deleteByProjectRoot(String projectRoot);
}
